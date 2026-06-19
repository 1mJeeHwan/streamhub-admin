package org.streamhub.api.v1.content;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.v1.content.entity.Hashtag;
import org.streamhub.api.v1.content.repository.HashtagRepository;

/**
 * Inserts a hashtag in its own transaction so a {@code name} unique-constraint collision from a
 * concurrent insert rolls back <i>only</i> this insert — never the caller's content
 * create/update transaction.
 *
 * <p>{@code getOrCreateHashtag} is a check-then-insert: two requests introducing the same new tag
 * name can both miss {@code findByName} and race to insert. Without isolation the loser's
 * {@code DataIntegrityViolationException} marks the enclosing content transaction rollback-only,
 * so the whole content save fails at commit even though the tag now exists. Running the insert in
 * {@link Propagation#REQUIRES_NEW} (and flushing) confines the collision to the inner transaction,
 * letting the caller absorb it and re-read the committed row.
 */
@Component
public class HashtagWriter {

    private final HashtagRepository hashtagRepository;

    public HashtagWriter(HashtagRepository hashtagRepository) {
        this.hashtagRepository = hashtagRepository;
    }

    /**
     * Persists a new hashtag with the given name in a fresh transaction, flushing so a unique-name
     * collision surfaces here (not on the outer commit).
     *
     * @param name the tag name to insert
     * @return the persisted hashtag (id populated)
     * @throws org.springframework.dao.DataIntegrityViolationException on a {@code name} collision
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Hashtag insert(String name) {
        return hashtagRepository.saveAndFlush(Hashtag.builder().name(name).build());
    }
}
