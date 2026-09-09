package com.assetmanagement.ops;

import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;

record PlatformJdbcEndpoint(String host, int port, String database, String username, String password) {

    static PlatformJdbcEndpoint parse(String jdbcUrl, String username, String password) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数据库备份/恢复仅支持 PostgreSQL");
        }
        String rest = jdbcUrl.substring("jdbc:postgresql://".length());
        int slash = rest.indexOf('/');
        String hostPort = slash < 0 ? rest : rest.substring(0, slash);
        String dbAndQuery = slash < 0 ? "postgres" : rest.substring(slash + 1);
        int query = dbAndQuery.indexOf('?');
        String database = query < 0 ? dbAndQuery : dbAndQuery.substring(0, query);
        if (database.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法从数据源解析数据库名");
        }
        String host;
        int port = 5432;
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0) {
            host = hostPort.substring(0, colon);
            try {
                port = Integer.parseInt(hostPort.substring(colon + 1));
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法从数据源解析数据库端口");
            }
        } else {
            host = hostPort;
        }
        if (host.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法从数据源解析数据库主机");
        }
        return new PlatformJdbcEndpoint(
                host,
                port,
                database,
                username == null ? "" : username,
                password == null ? "" : password
        );
    }
}
