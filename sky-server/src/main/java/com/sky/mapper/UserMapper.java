package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface UserMapper {

    //根据Openid来查询用户
    @Select("select * from user where openid=#{openid}")
    User getByOpenId(String openid);

    void insert(User user);

    @Select("select * from user where id=#{id}")
    User getById(Long userId);

    //根据时间区间统计用户数量
    Integer countByMap(Map map);
}
