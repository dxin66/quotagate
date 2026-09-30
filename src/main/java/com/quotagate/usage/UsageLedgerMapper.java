package com.quotagate.usage;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UsageLedgerMapper {

    @Insert("""
            INSERT INTO usage_ledger
                (request_id, tenant_id, provider, input_tokens,
                 output_tokens, event_type, created_at)
            VALUES
                (#{event.requestId}, #{event.tenantId}, #{event.provider},
                 #{event.inputTokens}, #{event.outputTokens},
                 #{event.eventType}, CURRENT_TIMESTAMP)
            """)
    int insert(@Param("event") UsageEvent event);

    @Insert({
            "<script>",
            "INSERT IGNORE INTO usage_ledger",
            "(request_id, tenant_id, provider, input_tokens,",
            " output_tokens, event_type, created_at)",
            "VALUES",
            "<foreach collection='events' item='event' separator=','>",
            "(#{event.requestId}, #{event.tenantId}, #{event.provider},",
            " #{event.inputTokens}, #{event.outputTokens},",
            " #{event.eventType}, CURRENT_TIMESTAMP)",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("events") List<UsageEvent> events);

        @Select("""
            SELECT COUNT(*)
            FROM usage_ledger
            WHERE request_id = #{requestId}
              AND event_type = #{eventType}
            """)
        int countByRequestAndEvent(
            @Param("requestId") String requestId,
            @Param("eventType") String eventType
        );
}
