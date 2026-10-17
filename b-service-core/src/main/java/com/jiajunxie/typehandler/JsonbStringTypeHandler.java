package com.jiajunxie.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * Stores validated JSON text in PostgreSQL json/jsonb columns without leaking PostgreSQL driver classes
 * into the service-core module. JDBC Types.OTHER lets the PostgreSQL driver bind the value as jsonb.
 */
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {
  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
    ps.setObject(i, parameter, Types.OTHER);
  }

  @Override
  public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return rs.getString(columnName);
  }

  @Override
  public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return rs.getString(columnIndex);
  }

  @Override
  public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return cs.getString(columnIndex);
  }
}
