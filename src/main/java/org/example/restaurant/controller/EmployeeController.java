package org.example.restaurant.controller;

import java.util.HashMap;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.JwtUtil;
import org.example.restaurant.common.Result;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.dto.EmployeeAddDTO;
import org.example.restaurant.dto.EmployeeLoginDTO;
import org.example.restaurant.dto.EmployeeUpdateDTO;
import org.example.restaurant.entity.Employee;
import org.example.restaurant.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/employees")
@Tag(name="员工管理（含登录认证）",description = "员工账号CRUD + 登录认证(JWT含角色声明) + 密码修改")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @GetMapping
    @Operation(summary = "查询所有员工",description = "获取员工列表")
    public Result<List<Employee>> list(){
        return Result.success(employeeService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单个员工",description = "根据ID获取员工信息")
    public Result<Employee> getById(@PathVariable Long id){
        return Result.success(employeeService.getById(id));
    }


    @PostMapping("/login")
    @Operation(summary = "员工登录",description = "根据用户名和密码登录，返回JWT token")
    public Result<Map<String,Object>> login(@Valid @RequestBody EmployeeLoginDTO dto){

        //使用dto获取参数
        Employee emp = employeeService.login(dto.getUsername(),dto.getPassword());

        //String token= UUID.randomUUID().toString();
        //token 的作用：身份凭证:UUID这个包可以生成唯一的标识码
        //使用JWT生成token（含角色信息）
        // role 为 null 说明数据异常（如被 update 清空），拒绝登录避免越权
        if (emp.getRole() == null) {
            throw new BusinessException("账号角色未配置，请联系管理员");
        }
        String token= JwtUtil.generateToken(emp.getId(), emp.getRole());
        //UUID ：像一张没有名字、没有有效期的门禁卡，每次进门都要查登记本。
        //JWT ：像一张写着你名字、有有效期的智能门禁卡，保安看一眼就知道你是谁。
        //替换后，系统会更安全、更高效

        
        Map<String,Object> data=new HashMap<>();
        data.put("token",token);
        data.put("name",emp.getName());

        return Result.success(data);//只把想要展示的内容返回
    }


    @PostMapping
    @Operation(summary = "新增员工",description = "添加新员工")
    public Result<String> add(@Valid @RequestBody EmployeeAddDTO dto){
        // 仅管理员可操作员工账号
        Integer role = UserContext.getRole();
        if (role == null || role != 1) {
            throw new BusinessException("仅管理员可操作员工账号");
        }
        Employee employee = new Employee();
        employee.setUsername(dto.getUsername());
        employee.setPassword(dto.getPassword());
        employee.setName(dto.getName());
        employee.setPhone(dto.getPhone());
        if (dto.getRole() != null) employee.setRole(dto.getRole());
        if (dto.getStatus() != null) employee.setStatus(dto.getStatus());
        employeeService.add(employee);
        return Result.success("添加成功");
    }

    @PutMapping
    @Operation(summary = "修改员工", description = "更新员工信息")
    public Result<String> update(@Valid @RequestBody EmployeeUpdateDTO dto){
        // 仅管理员可操作员工账号
        Integer role = UserContext.getRole();
        if (role == null || role != 1) {
            throw new BusinessException("仅管理员可操作员工账号");
        }
        Employee employee = new Employee();
        employee.setId(dto.getId());
        employee.setUsername(dto.getUsername());
        employee.setName(dto.getName());
        employee.setPhone(dto.getPhone());
        if (dto.getRole() != null) employee.setRole(dto.getRole());
        if (dto.getStatus() != null) employee.setStatus(dto.getStatus());
        employeeService.update(employee);
        return Result.success("修改成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除员工",description = "根据ID删除员工")
    public Result<String> deleteById(@PathVariable Long id){
        // 仅管理员可操作员工账号
        Integer role = UserContext.getRole();
        if (role == null || role != 1) {
            throw new BusinessException("仅管理员可操作员工账号");
        }
        employeeService.deleteById(id);
        return Result.success("删除成功");
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码",description = "修改当前员工密码")
    public Result<String> updatePassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword
        ){
        Long employeeId = UserContext.getEmployeeId();
        employeeService.updatePassword(employeeId,oldPassword,newPassword);
        return Result.success("密码修改成功");
    }
}
