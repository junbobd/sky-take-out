package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;

import java.util.List;

public interface DishService {
    //新增菜品和对应口味数据
    public void saveWithFlavor(DishDTO dishDTO);

    //菜品分页查询
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    //菜品批量删除
    void deleteBatch(List<Long> ids);

    //根据id查询对应菜品和口味数据
    DishVO getByIdWithFlavor(Long id);

    //根据id修改菜品信息和口味信息
    void updateWithFlavor(DishDTO dishDTO);

    //根据分类id查询菜品
    List<Dish> list(Long categoryId);

    //条件查询菜品和口味
    List<DishVO> listWithFlavor(Dish dish);
}
