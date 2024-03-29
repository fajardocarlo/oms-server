package com.ohwow.oms.inventoryadjustmenthistory.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import com.ohwow.oms.inventoryadjustmenthistory.dao.InventoryAdjustmentHistoryRepository;
import com.ohwow.oms.inventoryadjustmenthistory.domain.InventoryAdjustmentHistory;
import com.ohwow.oms.inventoryadjustmenthistory.dto.InventoryAdjustmentDto;
import com.ohwow.oms.inventoryadjustmenthistory.dto.InventoryAdjustmentHistoryResponse;

@Service
@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
public class InventoryAdjustmentHistoryService {

	@Autowired
	InventoryAdjustmentHistoryRepository inventoryAdjustmentHistoryRepository;

	public boolean addInventoryAdjustmentEntry(long productId, InventoryAdjustmentDto inventoryAdjustmentDto) {
		InventoryAdjustmentHistory inventoryAdjustmentHistory = new InventoryAdjustmentHistory(inventoryAdjustmentDto);
		inventoryAdjustmentHistory.setDateTimeAdjusted(LocalDateTime.now());
		if (ObjectUtils.isEmpty(inventoryAdjustmentHistoryRepository.saveAndFlush(inventoryAdjustmentHistory)))
			return true;
		else
			return false;

	}

	public InventoryAdjustmentHistoryResponse getInventoryAdjustmentsById(int page, int rows, long id) {

		Pageable pageable = PageRequest.of(page, rows);
		Page<InventoryAdjustmentDto> adjustments = inventoryAdjustmentHistoryRepository
				.findAllInventoryAdjustmentsByProductId(pageable, id);

		return new InventoryAdjustmentHistoryResponse(adjustments.getTotalPages(), adjustments.getContent());

	}

	public InventoryAdjustmentHistoryResponse getInventoryAdjustments(int page, int rows) {

		Pageable pageable = PageRequest.of(page, rows);
		Page<InventoryAdjustmentDto> adjustments = inventoryAdjustmentHistoryRepository
				.findAllInventoryAdjustments(pageable);

		return new InventoryAdjustmentHistoryResponse(adjustments.getTotalPages(), adjustments.getContent());

	}

}
