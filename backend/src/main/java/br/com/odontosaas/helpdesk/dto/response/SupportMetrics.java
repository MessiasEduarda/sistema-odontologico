package br.com.odontosaas.helpdesk.dto.response;

import java.math.BigDecimal;

public record SupportMetrics(
        int open,
        int urgentOpen,
        int overdue,
        int waitingClinic,
        int unread,
        int openedLast30Days,
        Integer avgFirstResponseMinutes,
        BigDecimal avgRating,
        int ratedCount) {}
