package com.jiajunxie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.LoanProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LoanProfileMapper extends BaseMapper<LoanProfile> {

  @Select(
      """
      SELECT *
      FROM bills.b_loan_profiles
      WHERE account_id = #{accountId}
      FOR UPDATE
      """)
  LoanProfile selectByIdForUpdate(@Param("accountId") String accountId);
}
