package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    /**
     * 动态查询购物车是否已有对应商品
     * @param shoppingCart
     * @return
     */
    List<ShoppingCart> list(ShoppingCart  shoppingCart);

    /**
     * 根据id修改商品数量
     * @param cart
     */
    @Update("update shopping_cart set number=#{number} where id=#{id}")
    void updateNumberById(ShoppingCart cart);

    /**
     * 插入购物车数据
     * @param cart
     */
    @Insert("insert into shopping_cart(name, image, user_id, dish_id, setmeal_id, dish_flavor, amount, create_time)" +
            "values" +
            "(#{name},#{image},#{userId},#{dishId},#{setmealId},#{dishFlavor},#{amount},#{createTime})")
    void insert(ShoppingCart cart);


    /**
     * 根据用户id清空购物车
     * @param userId
     */
    @Delete("delete from shopping_cart where user_id=#{userId}")
    void deleteByUserId(Long userId);


    /**
     * 根据菜品id和用户id删除购物车中的商品
     * @param cart
     */
    @Delete("delete from shopping_cart where dish_id=#{dishId} and user_id=#{userId}")
    void deleteByDishId(ShoppingCart cart);

    /**
     * 根据套餐id和用户id删除购物车中的套餐
     * @param cart
     */
    @Delete("delete from shopping_cart where setmeal_id=#{setmealId} and user_id=#{userId}")
    void deleteBySetmealId(ShoppingCart cart);

    /**
     * 根据id删除购物车中的商品
     *
     * @param id
     */
    @Delete("delete from shopping_cart where id=#{id}")
    void deleteById(Long id);


    /**
     * 批量插入购物车数据
     * @param shoppingCarts
     */
    void insertBatch(List<ShoppingCart> shoppingCarts);
}
