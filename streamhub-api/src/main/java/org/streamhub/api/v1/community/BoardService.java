package org.streamhub.api.v1.community;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.community.dto.BoardDto;
import org.streamhub.api.v1.community.entity.Board;
import org.streamhub.api.v1.community.repository.BoardRepository;

/**
 * Community board management: admin CRUD over the demo board set. The dataset is small, so the
 * listing loads all boards and sorts in memory by {@code sortOrder} (then id).
 */
@Service
public class BoardService {

    private final BoardRepository boardRepository;

    public BoardService(BoardRepository boardRepository) {
        this.boardRepository = boardRepository;
    }

    /** Admin listing: all boards, ordered by sortOrder then id. */
    @Transactional(readOnly = true)
    public List<BoardDto> listAll() {
        return boardRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Board::getSortOrder).thenComparing(Board::getId))
                .map(BoardDto::from)
                .toList();
    }

    @Transactional
    public BoardDto create(BoardDto request) {
        if (boardRepository.existsByCode(request.getCode())) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "이미 존재하는 게시판 코드입니다");
        }
        Board board = Board.builder()
                .code(request.getCode())
                .name(request.getName())
                .readLevel(request.getReadLevel())
                .writeLevel(request.getWriteLevel())
                .useYn(defaultYn(request.getUseYn()))
                .sortOrder(request.getSortOrder())
                .build();
        Board saved = boardRepository.save(board);
        return BoardDto.from(saved);
    }

    @Transactional
    public BoardDto update(Long id, BoardDto request) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (!board.getCode().equals(request.getCode()) && boardRepository.existsByCode(request.getCode())) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "이미 존재하는 게시판 코드입니다");
        }
        board.update(
                request.getCode(), request.getName(), request.getReadLevel(),
                request.getWriteLevel(), defaultYn(request.getUseYn()), request.getSortOrder());
        boardRepository.saveAndFlush(board);
        return BoardDto.from(board);
    }

    @Transactional
    public void delete(Long id) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        boardRepository.delete(board);
    }

    private String defaultYn(String value) {
        return value == null || value.isBlank() ? "Y" : value;
    }
}
