package com.quotagate.route;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ModelRouteMapper {

    @Select("""
            SELECT id, model_alias, provider, upstream_model,
                   priority, enabled, input_price, output_price
            FROM model_route
            WHERE model_alias = #{alias} AND enabled = TRUE
            ORDER BY priority ASC, id ASC
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class, id = true),
            @Arg(column = "model_alias", javaType = String.class),
            @Arg(column = "provider", javaType = String.class),
            @Arg(column = "upstream_model", javaType = String.class),
            @Arg(column = "priority", javaType = int.class),
            @Arg(column = "enabled", javaType = boolean.class),
            @Arg(column = "input_price", javaType = BigDecimal.class),
            @Arg(column = "output_price", javaType = BigDecimal.class)
    })
    List<ModelRoute> findEnabledByAlias(@Param("alias") String alias);

    @Select("""
            SELECT DISTINCT model_alias
            FROM model_route
            WHERE enabled = TRUE
            ORDER BY model_alias ASC
            """)
    List<String> findEnabledAliases();
}
