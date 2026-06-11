package com.knowflow.service;

import com.knowflow.vo.DashboardStatsVO;

public interface DashboardService {

    DashboardStatsVO getStats(Long userId);
}
