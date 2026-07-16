package org.example.restaurant.service.impl;

import org.example.restaurant.entity.OrderDetail;
import org.example.restaurant.entity.Orders;
import org.example.restaurant.service.PrintService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 打印服务实现 — 规划 Day3
 * 生成 ESC/POS 格式小票，当前输出到控制台模拟打印
 * 未来对接真实打印机只需修改 sendToPrinter 方法
 */
@Service
public class PrintServiceImpl implements PrintService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void printOrder(Orders order, List<OrderDetail> details) {
        String receipt = buildReceipt(order, details);
        sendToPrinter(receipt);
    }

    /**
     * 构建 ESC/POS 格式小票文本
     */
    private String buildReceipt(Orders order, List<OrderDetail> details) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("================================\n");
        sb.append("        在线餐饮管理平台\n");
        sb.append("================================\n");
        sb.append("桌号: ").append(order.getTableId()).append("\n");
        sb.append("订单号: ").append(order.getId()).append("\n");
        sb.append("时间: ").append(order.getCreateTime() != null
                ? order.getCreateTime().format(DTF) : "---").append("\n");
        sb.append("--------------------------------\n");
        sb.append("名称         数量    单价    金额\n");
        sb.append("--------------------------------\n");

        for (OrderDetail detail : details) {
            sb.append(String.format("%-10s  %-4d   %-6s  %s\n",
                    "菜品#" + detail.getDishId(),
                    detail.getAmount(),
                    detail.getPrice(),
                    detail.getPrice().multiply(BigDecimal.valueOf(detail.getAmount()))));
        }

        sb.append("--------------------------------\n");
        sb.append("合计: ").append(order.getTotalAmount()).append(" 元\n");
        sb.append("================================\n");
        sb.append("   谢谢惠顾，欢迎再次光临！\n");
        sb.append("================================\n");
        // ESC/POS 切纸指令（真实打印时使用）
        // sb.append("\n\n\n\n");  // 走纸4行后切纸

        return sb.toString();
    }

    /**
     * 加菜补充小票 — 仅打印新增菜品，标注"加菜"
     */
    @Override
    public void printAddItems(Orders order, List<OrderDetail> newDetails) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("================================\n");
        sb.append("        【加  菜】\n");
        sb.append("================================\n");
        sb.append("桌号: ").append(order.getTableId()).append("\n");
        sb.append("订单号: ").append(order.getId()).append("\n");
        sb.append("时间: ").append(order.getCreateTime() != null
                ? order.getCreateTime().format(DTF) : "---").append("\n");
        sb.append("--------------------------------\n");
        sb.append("名称         数量    单价    金额\n");
        sb.append("--------------------------------\n");

        for (OrderDetail detail : newDetails) {
            sb.append(String.format("%-10s  %-4d   %-6s  %s\n",
                    "菜品#" + detail.getDishId(),
                    detail.getAmount(),
                    detail.getPrice(),
                    detail.getPrice().multiply(BigDecimal.valueOf(detail.getAmount()))));
        }

        sb.append("--------------------------------\n");
        sb.append("本次加菜: ").append(
                newDetails.stream()
                        .map(d -> d.getPrice().multiply(BigDecimal.valueOf(d.getAmount())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        ).append(" 元\n");
        sb.append("订单总额: ").append(order.getTotalAmount()).append(" 元\n");
        sb.append("================================\n");
        sb.append("   加菜确认，请继续享用！\n");
        sb.append("================================\n");

        sendToPrinter(sb.toString());
    }

    /**
     * 发送到打印机
     * 未来对接真实打印机时仅需修改此方法：
     * 1. 通过串口/网络发送 ESC/POS 字节
     * 2. 或调用厂商 SDK 直接打印
     */
    private void sendToPrinter(String content) {
        System.out.println("===== 打印小票 =====" + content + "===================");
    }
}
