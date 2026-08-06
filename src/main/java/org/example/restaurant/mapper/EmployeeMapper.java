package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.Employee;

import java.util.List;

@Mapper
public interface EmployeeMapper {

    @Select("SELECT * FROM employee")
    List<Employee> findAll();

    @Select("SELECT * FROM employee WHERE id=#{id}")
    Employee findById(Long id);

    @Select("SELECT * FROM employee WHERE username=#{username}")
    //AND password=#{password}；不要使用两个参数去查找用户：会导致用户名错误会返回null,密码错误也返回null：用户无法判断是哪个错误
    Employee findByUserName(String username);

    @Insert("INSERT INTO employee (username,password,name,phone,status,role,create_time,update_time) " +
            "VALUES (#{username},#{password},#{name},#{phone},#{status},#{role},#{createTime},#{updateTime})")
    void add(Employee employee);

    @Update("UPDATE employee SET " +
            "username=#{username},name=#{name},phone=#{phone},status=#{status},role=#{role},update_time=#{updateTime} WHERE id=#{id}")
    void update(Employee employee);

    @Update("UPDATE employee SET password=#{password} WHERE id=#{id}")
    void updatePassword(Employee employee);


    @Delete("DELETE FROM employee WHERE id=#{id}")
    void deleteById(Long id);

    @Select("SELECT COUNT(*) FROM employee WHERE role=1")
    int countAdmins();
}
