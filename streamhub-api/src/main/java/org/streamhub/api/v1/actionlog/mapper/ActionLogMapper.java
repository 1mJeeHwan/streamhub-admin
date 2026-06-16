package org.streamhub.api.v1.actionlog.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.actionlog.dto.ActionLogItem;

/** MyBatis mapper for the audit-log list. Maps to {@code resources/mappers/ActionLogMapper.xml}. */
@Mapper
public interface ActionLogMapper {

    List<ActionLogItem> selectList(
            @Param("action") String action,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size);

    long countList(@Param("action") String action, @Param("keyword") String keyword);
}
