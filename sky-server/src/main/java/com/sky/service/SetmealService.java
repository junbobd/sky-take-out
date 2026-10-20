package com.sky.service;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.SetmealVO;

import java.util.List;

public interface SetmealService {
    //新增套餐
    void saveWithDish(SetmealDTO setmealDTO);

    //套餐分页查询
    PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    //批量删除套餐
    void deleteBatch(List<Long> ids);

    //根据套餐id回显数据
    SetmealVO getByIdWithDish(Long id);

    //修改套餐
    void update(SetmealDTO setmealDTO);
}
