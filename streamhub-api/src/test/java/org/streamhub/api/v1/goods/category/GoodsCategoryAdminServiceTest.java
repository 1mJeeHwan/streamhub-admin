package org.streamhub.api.v1.goods.category;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.goods.entity.GoodsCategory;
import org.streamhub.api.v1.goods.repository.GoodsItemRepository;

@ExtendWith(MockitoExtension.class)
class GoodsCategoryAdminServiceTest {

    @Mock
    private GoodsCategoryAdminRepository goodsCategoryAdminRepository;

    @Mock
    private GoodsItemRepository goodsItemRepository;

    @Mock
    private ActionLogPublisher actionLogPublisher;

    @InjectMocks
    private GoodsCategoryAdminService goodsCategoryAdminService;

    private GoodsCategory category() {
        GoodsCategory c = GoodsCategory.builder().name("의류").depth(1).sort(0).useYn("Y").build();
        ReflectionTestUtils.setField(c, "id", 1L);
        return c;
    }

    @Test
    void delete_withChildCategories_throwsInvalidParameter() {
        when(goodsCategoryAdminRepository.findById(1L)).thenReturn(Optional.of(category()));
        when(goodsCategoryAdminRepository.countByParentId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> goodsCategoryAdminService.delete(1L))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.INVALID_PARAMETER);

        // The orphan guard must short-circuit before any delete is issued.
        verify(goodsCategoryAdminRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_referencedByGoods_throwsInvalidParameter() {
        when(goodsCategoryAdminRepository.findById(1L)).thenReturn(Optional.of(category()));
        when(goodsCategoryAdminRepository.countByParentId(1L)).thenReturn(0L);
        when(goodsItemRepository.countByCategoryId(1L)).thenReturn(5L);

        assertThatThrownBy(() -> goodsCategoryAdminService.delete(1L))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.INVALID_PARAMETER);

        verify(goodsCategoryAdminRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_missing_throwsNotFound() {
        when(goodsCategoryAdminRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goodsCategoryAdminService.delete(99L))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    void delete_noDependents_deletesAndLogs() {
        GoodsCategory category = category();
        when(goodsCategoryAdminRepository.findById(1L)).thenReturn(Optional.of(category));
        when(goodsCategoryAdminRepository.countByParentId(1L)).thenReturn(0L);
        when(goodsItemRepository.countByCategoryId(1L)).thenReturn(0L);

        goodsCategoryAdminService.delete(1L);

        verify(goodsCategoryAdminRepository).delete(category);
        verify(actionLogPublisher).publish("GOODS_CATEGORY_DELETE", "GOODS_CATEGORY", "1", "의류");
    }
}
