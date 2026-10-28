package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    //根据Openid来查询用户
    @Select("select * from user where openid=#{openid}")
    User getByOpenId(String openid);

    void insert(User user);
}
