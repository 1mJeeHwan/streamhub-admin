package org.streamhub.api.v1.actionlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.v1.actionlog.dto.ActionLogItem;
import org.streamhub.api.v1.actionlog.dto.ActionLogSearchRequest;

/**
 * Remote reader: calls the audit service's {@code /v1/action-logs} and maps its page JSON back into a
 * {@link ResInfinityList}; an audit-service failure surfaces as a clear {@link ApiException}, not a
 * raw 500.
 */
class RemoteActionLogReaderTest {

    private static final String URL = "http://audit:8090";

    @Test
    void list_fetchesFromAuditService_andMapsPage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RemoteActionLogReader reader = new RemoteActionLogReader(builder, URL);

        String json = "{\"contents\":[{\"id\":1,\"adminId\":7,\"adminName\":\"관리자\","
                + "\"action\":\"MEMBER_APPROVE\",\"targetType\":\"MEMBER\",\"targetId\":\"42\","
                + "\"detail\":\"회원 승인\",\"ip\":\"1.2.3.4\",\"createdAt\":\"2026-06-22T10:00:00\"}],"
                + "\"totalCount\":1,\"totalPage\":1}";
        server.expect(requestTo(startsWith(URL + "/v1/action-logs")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ResInfinityList<ActionLogItem> result =
                reader.list(new ActionLogSearchRequest(0, 10, null, null));

        server.verify();
        assertThat(result.getContents()).hasSize(1);
        assertThat(result.getContents().get(0).getAction()).isEqualTo("MEMBER_APPROVE");
        assertThat(result.getTotalCount()).isEqualTo(1);
    }

    @Test
    void list_auditServiceDown_raisesApiException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RemoteActionLogReader reader = new RemoteActionLogReader(builder, URL);

        server.expect(requestTo(startsWith(URL + "/v1/action-logs")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> reader.list(new ActionLogSearchRequest(0, 10, null, null)))
                .isInstanceOf(ApiException.class);
    }
}
