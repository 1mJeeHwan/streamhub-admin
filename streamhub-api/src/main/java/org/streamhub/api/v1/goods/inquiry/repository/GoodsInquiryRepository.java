package org.streamhub.api.v1.goods.inquiry.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.goods.inquiry.entity.AnswerStatus;
import org.streamhub.api.v1.goods.inquiry.entity.GoodsInquiry;

/** JPA repository for {@link GoodsInquiry} (goods Q&A). */
public interface GoodsInquiryRepository extends JpaRepository<GoodsInquiry, Long> {

    List<GoodsInquiry> findByAnswerStatusOrderByIdDesc(AnswerStatus answerStatus);

    List<GoodsInquiry> findAllByOrderByIdDesc();

    /** A member's own inquiries, most recent first (the "내 문의" feed). */
    List<GoodsInquiry> findByMemberIdOrderByIdDesc(Long memberId);
}
