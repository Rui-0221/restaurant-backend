package org.example.restaurant.service.impl;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.PasswordEncoderUtil;
import org.example.restaurant.entity.Employee;
import org.example.restaurant.mapper.EmployeeMapper;
import org.example.restaurant.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Override
    public List<Employee> list(){
        return employeeMapper.findAll();
    }

    @Override
    public Employee getById(Long id){
        Employee employee=employeeMapper.findById(id);
        if(employee==null){
            throw new BusinessException("雇员不存在:id="+id);
        }
        return employee;
    }

    @Override
    public Employee login(String username,String password){
        Employee employee=employeeMapper.findByUserName(username);

        //添加验证密码是否正确的功能（使用BCrypt比较）
        if(employee==null||!PasswordEncoderUtil.matches(password,employee.getPassword())){
            throw new BusinessException("用户名或密码错误");
        }
        /*
        if(employee==null){
            throw new BusinessException("雇员不存在:username="+username);
        }
        if(!employee.getPassword().equals(password)){
            throw new BusinessException("密码错误");
        }
        */
        if(employee.getStatus()==0){
            throw new BusinessException("账号已禁用");
        }

        return employee;
    }



    //修改了新增员工的逻辑：先判断员工是否已存在，如果存在则报错，在将员工密码加密后存入密码，之后再添加员工到数据库
    @Override
    public void add(Employee employee){
        //根据用户名查询用户是否已经存在
        Employee existing=employeeMapper.findByUserName(employee.getUsername());
        if(existing !=null){
            throw new BusinessException("用户名已存在");
        }
        //在创建新用户时就对用户密码进行加密
        employee.setPassword(PasswordEncoderUtil.encode(employee.getPassword()));//使用加密后的密码作为这个用户的新密码（对原密码进行加密）

        LocalDateTime now=LocalDateTime.now();
        employee.setCreateTime(now);
        employee.setUpdateTime(now);
        employeeMapper.add(employee);
    }

    @Override
    public void update(Employee employee){
        Employee existing = employeeMapper.findById(employee.getId());
        if(existing==null){
            throw new BusinessException("员工不存在");
        }
        // 密码通过 updatePassword 专用方法修改，此处不更新密码字段
        employee.setPassword(null);
        employee.setUpdateTime(LocalDateTime.now());

        employeeMapper.update(employee);
    }

    @Override
    public  void deleteById(Long id){
        Employee employee=employeeMapper.findById(id);
        if(employee==null){
            throw new BusinessException("雇员不存在id="+id);
        }
        else{
            employeeMapper.deleteById(id);
        }
    }

    @Override
    public void updatePassword(Long employeeId,String oldPassword,String newPassword){
        Employee employee=employeeMapper.findById(employeeId);
        if(employee==null){
            throw new BusinessException("员工不存在");
        }

        //验证旧密码是否正确:
        if(!PasswordEncoderUtil.matches(oldPassword,employee.getPassword())){
            throw new BusinessException("旧密码错误");
        }

        //旧密码正确，则替换新密码（通过更新员工信息实现）
        //加密新密码并保存
        employee.setPassword(PasswordEncoderUtil.encode(newPassword));
        employeeMapper.updatePassword(employee);
    }
}
