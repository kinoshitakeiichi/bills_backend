package com.jiajunxie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.TransactionAllocation;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TransactionAllocationMapper extends BaseMapper<TransactionAllocation> {
  @Select("SELECT * FROM bills.b_transaction_allocations WHERE transaction_id = #{transactionId} ORDER BY created_at")
  List<TransactionAllocation> selectByTransactionId(String transactionId);

  @Delete("DELETE FROM bills.b_transaction_allocations WHERE transaction_id = #{transactionId}")
  int deleteByTransactionId(String transactionId);
}
