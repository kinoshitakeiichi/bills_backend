package com.jiajunxie.convert;

import com.jiajunxie.entity.Person;
import com.jiajunxie.vo.PersonVo;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PersonConvertServiceMapper {
  PersonVo toVo(Person source);

  List<PersonVo> toVos(List<Person> source);

  Person toEntity(PersonVo source);

  void updateEntity(PersonVo source, @MappingTarget Person target);
}
