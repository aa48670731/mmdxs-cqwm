package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    /**
     * 新增菜品和对应的口味
     *
     * @param dishDTO
     */
    //由于涉及到两张表：菜品和菜品口味表，有可能出现数据一致性问题，所以需要开启事务
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveWithFlavor(DishDTO dishDTO) {
        //取得菜品实体对象
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        //向菜品表中插入1条数据
        dishMapper.insert(dish);
        //上面语句是用了主键回传技术，可以获得插入之后自动分配的主键了
        Long id = dish.getId();

        List<DishFlavor> flavors = dishDTO.getFlavors();
//        flavors.forEach(new Consumer<DishFlavor>() {
//            @Override
//            public void accept(DishFlavor dishFlavor) {
//                dishFlavor.setDishId(id);
//            }
//        });

        //传过来的口味不为空，才进行设置id并插入
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(id);
            });
            //向口味表中插入n条数据(1个菜品有多个口味)
            //不用一条一条插入，直接批量插入
            dishFlavorMapper.insertBatch(flavors);

        }
    }

    /**
     * 菜品分类查询
     *
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除菜品
     *
     * @param ids
     */
    @Transactional
    @Override
    public void deleteBatch(List<Long> ids) {
        //判断当前菜品是否能被删除？是否为停售状态？
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            //如果查不到菜品呢？前端传过来的id都是对应的，不会出现查不到的情况
            if (dish.getStatus() == StatusConstant.ENABLE) {
                //菜品处于起售状态，不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        //判断当前菜品是否能被删除？是否有关联套餐
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && setmealIds.size() > 0) {
            //当前菜品关联了套餐，不允许删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        //删除菜品信息
//        for (Long id : ids) {
//            dishMapper.deleteById(id);
//            //删除菜品关联的口味信息
//            dishFlavorMapper.deletByDishId(id);
//        }
        //优化删除菜品信息的逻辑
        //根据菜品id集合批量删除菜品
        dishMapper.deleteByIds(ids);
        //根据菜品id集合批量删除关联口味数据
        dishFlavorMapper.deletByDishIds(ids);
    }

    /**
     * 根据id查询菜品
     *
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        //根据id查询菜品数据
        Dish dish = dishMapper.getById(id);
        //根据菜品id查询口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        //将查询到的数据封装到VO
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);
        return dishVO;
    }

    /**
     * 根据id修改菜品基本信息和对应的口味信息
     *
     * @param dishDTO
     */
    @Transactional
    @Override
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        //修改菜品的基本信息
        dishMapper.update(dish);
        //修改口味表
        //只需把对应菜品的口味全删除，然后加上dto里的口味数据就可以了
        //这样对于口味表的更新就简单无脑了
        dishFlavorMapper.deletByDishId(dishDTO.getId());

        List<DishFlavor> flavors = dishDTO.getFlavors();
        //传过来的口味不为空，才进行设置id并插入
        if (flavors != null && flavors.size() > 0) {
            //以防万一前端在传过来口味的数据时忘记传入菜品id
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishDTO.getId());
            });
            //向口味表中插入n条数据(1个菜品有多个口味)
            //不用一条一条插入，直接批量插入
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @Override
    public List<Dish> getByCategoryId(Long categoryId) {
//        List<Dish> dishes = dishMapper.getByCategoryId(categoryId);
        //作业答案，使用分类id来进行动态查询
        //由于添加套餐的时候我们不希望添加未起售的菜品，所以设置搜寻的状态为起售状态
        Dish dish = Dish.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();
        return dishMapper.list(dish);
    }

    /**
     * 菜品起售、停售
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        //复用mapper层的update接口
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        dishMapper.update(dish);
    }

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }

}
