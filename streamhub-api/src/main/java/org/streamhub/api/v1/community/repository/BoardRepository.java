package org.streamhub.api.v1.community.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.community.entity.Board;

/** JPA repository for {@link Board} (community boards). */
public interface BoardRepository extends JpaRepository<Board, Long> {

    List<Board> findByUseYn(String useYn);

    Optional<Board> findByCode(String code);

    boolean existsByCode(String code);
}
