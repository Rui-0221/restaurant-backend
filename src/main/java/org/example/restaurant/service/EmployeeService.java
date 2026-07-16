package org.example.restaurant.service;

import org.example.restaurant.entity.Employee;

import java.util.List;

public interface EmployeeService {
    List<Employee> list();
    Employee getById(Long id);
    Employee login(String username,String password);
    void add(Employee employee);
    void update(Employee employee);
    void deleteById(Long id);
    //添加修改密码的方法
    void updatePassword(Long employeeId,String oldPassword,String newPassword);
}
