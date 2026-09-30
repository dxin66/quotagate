package com.quotagate.auth;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface ApiKeyMapper {

    @Select("""
            SELECT tenant_id, status, expires_at
            FROM api_key
            WHERE key_hash = #{hash}
            """)
    @ConstructorArgs({
            @Arg(column = "tenant_id", javaType = long.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "expires_at", javaType = LocalDateTime.class)
    })
    ApiKey findByHash(@Param("hash") String hash);
}