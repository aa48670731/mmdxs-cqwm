package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐
     *
     * @param setmealDTO
     */
    @Transactional //多表操作，加上事务保证数据一致性
    @Override
    public void save(SetmealDTO setmealDTO) {
        //套餐价格？是根据前端传过来的设置还是后端进行菜品原价求和设置？
        //套餐包含菜品，涉及到套餐表和套餐-菜品表
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //更新套餐表
        setmealMapper.insert(setmeal);
        //插入后通过主键回传得到主键
        Long setmealId = setmeal.getId();

        //取出套餐与菜品关系
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();

        //更新套餐-菜品表
        //如果套餐关联菜品不为空
        if (setmealDishes != null && setmealDishes.size() > 0) {
            //设定套餐的id
            setmealDishes.forEach(setmealDish -> {
                setmealDish.setSetmealId(setmealId);
            });
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    /**
     * 分页查询套餐
     *
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除套餐
     *
     * @param ids
     */
    @Transactional
    @Override
    public void deleteBatch(List<Long> ids) {
        //删除套餐不需要将菜品一起删除，但是要将和套餐关联的菜品删除
        //涉及到套餐表和套餐-菜品表
        //套餐只有在停售的时候能被删除
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.getById(id);
            //如果套餐起售中，不能删除
            if (setmeal.getStatus() == 1) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
//        //更优美的方式：
//        ids.forEach(id -> {
//            Setmeal setmeal = setmealMapper.getById(id);
//            //如果套餐起售中，不能删除
//            if (setmeal.getStatus() == 1) {
//                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
//            }
//        });

        //套餐表中删除套餐
        setmealMapper.deleteBatch(ids);

        //套餐-菜品表中删除对应关系
        setmealDishMapper.deleteBySetmealIds(ids);

//        //另一种方法，一个一个删除：
//        ids.forEach(setmealId -> {
//            //删除套餐表中的数据
//            setmealMapper.deleteById(setmealId);
//            //删除套餐菜品关系表中的数据
//            setmealDishMapper.deleteBySetmealId(setmealId);
//        });
    }

    /**
     * 根据id查询套餐,需要展示两部分信息，分别来自套餐表和套餐-菜品表
     *
     * @param id
     * @return
     */
    @Override
    public SetmealVO getById(Long id) {
        //查套餐表
        Setmeal setmeal = setmealMapper.getById(id);
        //查套餐-菜品表
        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);

        SetmealVO setmealVO = new SetmealVO();
        //套餐部分信息拷贝
        BeanUtils.copyProperties(setmeal, setmealVO);
        //套餐和菜品关联信息拷贝
        setmealVO.setSetmealDishes(setmealDishes);

        return setmealVO;
    }

    /**
     * 修改套餐
     *
     * @param setmealDTO
     */
    @Transactional
    @Override
    public void update(SetmealDTO setmealDTO) {
        //修改套餐涉及两个表，一个套餐表，一个套餐-菜品表
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        //套餐表修改
        setmealMapper.update(setmeal);

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();

        //套餐-菜品表修改
        //简单无脑：先删除原有关联，后插入现有关联
        //删除原有关联
        setmealDishMapper.deleteBySetmealId(setmeal.getId());

        //插入现有关联
        //如果有才进行插入
        if (setmealDishes != null && setmealDishes.size() > 0) {
            setmealDishes.forEach(setmealDish -> {
                setmealDish.setSetmealId(setmeal.getId());
            });
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    /**
     * 套餐起售、停售
     *
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        //套餐起售条件：套餐里面的菜品都得是起售状态！！
//        //下面为自己写的
//        //查看套餐里的菜品：需要查询菜品表
//        //但要查询菜品表还需要查询到套餐对应菜品id，还得查询套餐-菜品表
//        //一共涉及两张表
//        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);
//        for (SetmealDish setmealDish : setmealDishes) {
//            Long dishId = setmealDish.getDishId();
//            Dish dish = dishMapper.getById(dishId);
//            //有菜品停售，不能启用套餐
//            if (dish.getStatus() == StatusConstant.DISABLE) {
//                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ENABLE_FAILED);
//            }
//        }
        //答案：优化改进sql语句：
        //实际上只涉及两张表就够了，菜品表和套餐-菜品表
        //由于上面涉及两个表的mapper层分别查询，损失性能比较高
        //最好一次性用一个方法直接查三张表
        //还有一个优化是：传过来的是起售，证明我现在的状态是停售，才需要进行里面菜品的检查
        //如果传过来的是停售，则无需检查套餐里的菜品是否起售
        if (status == StatusConstant.ENABLE) {
            List<Dish> dishList = dishMapper.getBySetmealId(id);
            //查到了的情况下再判断菜品是否起售
            if (dishList != null && dishList.size() > 0) {
                dishList.forEach(dish -> {
                    if (dish.getStatus() == StatusConstant.DISABLE) {
                        throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                });
            }
        }
        //起售套餐,复用update接口
        Setmeal setmeal = Setmeal
                .builder()
                .status(status)
                .id(id)
                .build();
        setmealMapper.update(setmeal);
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
