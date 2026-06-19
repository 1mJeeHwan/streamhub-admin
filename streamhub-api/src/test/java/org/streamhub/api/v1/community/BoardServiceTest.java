package org.streamhub.api.v1.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.community.dto.BoardDto;
import org.streamhub.api.v1.community.entity.Board;
import org.streamhub.api.v1.community.repository.BoardRepository;

/**
 * Unit tests for {@link BoardService} board code uniqueness: create rejects a duplicate code,
 * update guards a code change against an existing code but allows keeping the same code, and a
 * missing board surfaces {@code NOT_FOUND}.
 */
@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;

    @InjectMocks
    private BoardService boardService;

    private BoardDto request(String code, String name) {
        BoardDto dto = new BoardDto();
        dto.setCode(code);
        dto.setName(name);
        dto.setReadLevel(1);
        dto.setWriteLevel(1);
        dto.setSortOrder(0);
        return dto;
    }

    private Board board(String code) {
        return Board.builder().code(code).name("게시판").readLevel(1).writeLevel(1)
                .useYn("Y").sortOrder(0).build();
    }

    @Test
    void create_duplicateCode_isInvalidParameter() {
        when(boardRepository.existsByCode("notice")).thenReturn(true);

        assertThatThrownBy(() -> boardService.create(request("notice", "공지")))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.INVALID_PARAMETER);
        verify(boardRepository, never()).save(any(Board.class));
    }

    @Test
    void create_uniqueCode_persists() {
        when(boardRepository.existsByCode("free")).thenReturn(false);
        when(boardRepository.save(any(Board.class))).thenAnswer(inv -> inv.getArgument(0));

        BoardDto result = boardService.create(request("free", "자유"));

        assertThat(result.getCode()).isEqualTo("free");
        verify(boardRepository).save(any(Board.class));
    }

    @Test
    void update_changingToExistingCode_isInvalidParameter() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board("notice")));
        when(boardRepository.existsByCode("free")).thenReturn(true);

        assertThatThrownBy(() -> boardService.update(1L, request("free", "자유")))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.INVALID_PARAMETER);
        verify(boardRepository, never()).saveAndFlush(any(Board.class));
    }

    @Test
    void update_keepingSameCode_doesNotCheckExistence() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board("notice")));
        when(boardRepository.saveAndFlush(any(Board.class))).thenAnswer(inv -> inv.getArgument(0));

        BoardDto result = boardService.update(1L, request("notice", "공지 수정"));

        assertThat(result.getName()).isEqualTo("공지 수정");
        verify(boardRepository, never()).existsByCode(any());
        verify(boardRepository).saveAndFlush(any(Board.class));
    }

    @Test
    void update_missingBoard_isNotFound() {
        when(boardRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> boardService.update(99L, request("x", "x")))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.NOT_FOUND);
    }
}
