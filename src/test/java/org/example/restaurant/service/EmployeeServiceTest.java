package org.example.restaurant.service;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.entity.Employee;
import org.example.restaurant.mapper.EmployeeMapper;
import org.example.restaurant.service.impl.EmployeeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * “最后一名管理员”保护只依赖 Service 与 Mapper 的公开交互，使用单元测试
 * 避免为了验证守卫而修改共享本地库中的真实管理员账号。
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {
    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    private Employee existingAdmin;

    @BeforeEach
    void setUp() {
        existingAdmin = new Employee();
        existingAdmin.setId(1L);
        existingAdmin.setUsername("admin");
        existingAdmin.setName("管理员");
        existingAdmin.setPhone("13800000001");
        existingAdmin.setRole(1);
        existingAdmin.setStatus(1);
    }

    @Test
    void shouldRejectDemotingLastAdmin() {
        when(employeeMapper.findById(1L)).thenReturn(existingAdmin);
        when(employeeMapper.countAdmins()).thenReturn(1);
        Employee update = new Employee();
        update.setId(1L);
        update.setRole(2);

        assertThrows(BusinessException.class, () -> employeeService.update(update));

        verify(employeeMapper, never()).update(existingAdmin);
    }

    @Test
    void shouldRejectDisablingLastAdmin() {
        when(employeeMapper.findById(1L)).thenReturn(existingAdmin);
        when(employeeMapper.countAdmins()).thenReturn(1);
        Employee update = new Employee();
        update.setId(1L);
        update.setStatus(0);

        assertThrows(BusinessException.class, () -> employeeService.update(update));

        verify(employeeMapper, never()).update(existingAdmin);
    }

    @Test
    void shouldRejectDeletingLastAdmin() {
        when(employeeMapper.findById(1L)).thenReturn(existingAdmin);
        when(employeeMapper.countAdmins()).thenReturn(1);

        assertThrows(BusinessException.class, () -> employeeService.deleteById(1L));

        verify(employeeMapper, never()).deleteById(1L);
    }

    @Test
    void shouldAllowDemotingAdminWhenAnotherAdminExists() {
        when(employeeMapper.findById(1L)).thenReturn(existingAdmin);
        when(employeeMapper.countAdmins()).thenReturn(2);
        Employee update = new Employee();
        update.setId(1L);
        update.setRole(2);

        employeeService.update(update);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeMapper).update(captor.capture());
        assertEquals(2, captor.getValue().getRole());
    }
}
