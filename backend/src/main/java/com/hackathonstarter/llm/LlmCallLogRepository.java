package com.hackathonstarter.llm;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {}
