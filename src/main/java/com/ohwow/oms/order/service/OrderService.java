package com.ohwow.oms.order.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import com.ohwow.oms.customer.domain.Customer;
import com.ohwow.oms.customer.dto.CustomerDto;
import com.ohwow.oms.customer.service.CustomerService;
import com.ohwow.oms.inventory.exception.InventoryException;
import com.ohwow.oms.inventory.service.InventoryService;
import com.ohwow.oms.order.OrderStatusEnum;
import com.ohwow.oms.order.dao.OrderRepository;
import com.ohwow.oms.order.domain.Order;
import com.ohwow.oms.order.dto.OrderDto;
import com.ohwow.oms.order.dto.OrderResponseDto;
import com.ohwow.oms.order.dto.UpdateOrderStatusDto;
import com.ohwow.oms.order.exception.OrderException;
import com.ohwow.oms.orderdetails.dao.OrderDetailRepository;
import com.ohwow.oms.orderdetails.domain.OrderDetail;
import com.ohwow.oms.orderdetails.dto.OrderDetailDto;
import com.ohwow.oms.products.dao.ProductRepository;
import com.ohwow.oms.products.domain.Product;
import com.ohwow.oms.products.exception.ProductException;
import com.ohwow.oms.statushistory.dto.StatusHistoryDto;
import com.ohwow.oms.statushistory.service.StatusHistoryService;

@Service
@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
public class OrderService {

	@Autowired
	OrderRepository orderRepository;

	@Autowired
	CustomerService customerService;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	OrderDetailRepository orderDetailRepository;

	@Autowired
	InventoryService inventoryService;

	@Autowired
	StatusHistoryService statusHistoryService;

	/**
	 * Get all orders
	 * 
	 * @return all Orders
	 */
	public OrderResponseDto getAllOrders(int page, int rows) {

		Pageable pageable = PageRequest.of(page, rows);
		Page<Order> orders = orderRepository.findAllByOrderByIdDesc(pageable);
		List<OrderDto> orderDtos = new ArrayList<>();

		for (Order order : orders.getContent()) {
			List<OrderDetailDto> orderDetails = new ArrayList<>();
			for (OrderDetail orderDetail : order.getOrdeDetails()) {
				Product product = orderDetail.getProduct();
				orderDetails.add(new OrderDetailDto(product.getId(), product.getItemName(), orderDetail.getQuantity(),
						orderDetail.getProduct().getPrice(), orderDetail.getTotal(), orderDetail.hasEnoughStock()));
			}
			OrderDto orderDto = new OrderDto(order.getId(), new CustomerDto(order.getCustomer()), order.getCreatedBy(),
					order.getDateTimeCreated(), order.getModifiedBy(), order.getDateTimeModified(),
					order.getTotalPrice(), order.getOrderStatus(), orderDetails, null, order.getPaymentMethod(),
					order.getAdditionalNotes(), order.hasStockIssues());
			orderDtos.add(orderDto);
		}

		for (OrderDto orderqqq : orderDtos) {
			System.out.println(orderqqq.getOrderDetails().toString());
		}
		return new OrderResponseDto(orders.getTotalPages(), orderDtos);
	}

	/**
	 * Update order
	 * 
	 * @param order
	 * @return
	 */
	public boolean updateOrder(Order order) {

		boolean isSuccessful;

		if (!ObjectUtils.isEmpty(orderRepository.save(order))) {
			isSuccessful = true;
		} else {
			isSuccessful = false;
		}

		return isSuccessful;

	}

	/**
	 * update the status of the order
	 * 
	 * @param id
	 * @param orderStatus
	 * @return boolean true if successful
	 * @throws InventoryException
	 * @throws ProductException
	 */
	public boolean updateOrderStatus(long id, UpdateOrderStatusDto updateOrderStatus)
			throws InventoryException, ProductException {

		boolean isSuccessful = false;

		Optional<Order> orderResult = orderRepository.findById(id);

		if (orderResult.isPresent()) {
			Order order = orderResult.get();
			if (updateOrderStatus.getOrderStatus().equals(OrderStatusEnum.CANCELLED)) {
				for (OrderDetail orderDetail : order.getOrdeDetails()) {
					inventoryService.returnCommittedStock(orderDetail.getProduct().getId(), orderDetail.getQuantity());
				}
			} else if (updateOrderStatus.getOrderStatus().equals(OrderStatusEnum.COMPLETED)) {
				for (OrderDetail orderDetail : order.getOrdeDetails()) {
					inventoryService.deductFromStockOnHand(orderDetail.getProduct().getParentId(),
							orderDetail.getQuantity());
					orderDetail.setHasEnoughStock(true);
					orderDetailRepository.save(orderDetail);
				}

				order.setDateTimeCompleted(LocalDateTime.now());
				order.setHasStockIssues(false);
			}

			statusHistoryService.updateStatusAndHistory(order, updateOrderStatus.getOrderStatus(),
					updateOrderStatus.getUsername());
			order.setOrderStatus(updateOrderStatus.getOrderStatus());
			orderRepository.save(order);
			isSuccessful = true;
		} else
			throw new ProductException(ProductException.PRODUCT_NOT_FOUND_EXCEPTION);

		return isSuccessful;
	}

	/**
	 * Get order by order id
	 * 
	 * @param id
	 * @return
	 * @throws OrderException
	 */
	public OrderDto getOrderById(long id) throws OrderException {
		Optional<Order> orderResult = orderRepository.findById(id);
		if (orderResult.isPresent()) {
			Order order = orderResult.get();
			List<OrderDetailDto> orderDetails = order.getOrdeDetails().stream().map(OrderDetailDto::new)
					.collect(Collectors.toList());

			List<StatusHistoryDto> statusHistories = order.getStatusHistories().stream().map(StatusHistoryDto::new)
					.collect(Collectors.toList());

			return new OrderDto(order.getId(), new CustomerDto(order.getCustomer()), order.getCreatedBy(),
					order.getDateTimeCreated(), order.getModifiedBy(), order.getDateTimeModified(),
					order.getTotalPrice(), order.getOrderStatus(), orderDetails, statusHistories,
					order.getPaymentMethod(), order.getAdditionalNotes(), order.hasStockIssues());

		} else
			throw new OrderException(OrderException.ORDER_NOT_FOUND_EXCEPTION);
	}

	/**
	 * Get order status based on order id
	 * 
	 * @param id
	 * @return
	 * @throws OrderException
	 */
	public OrderStatusEnum getOrderStatusById(long id) throws OrderException {

		Optional<Order> orderResult = orderRepository.findById(id);
		if (orderResult.isPresent()) {
			return orderResult.get().getOrderStatus();
		} else
			throw new OrderException(OrderException.ORDER_NOT_FOUND_EXCEPTION);

	}

	/**
	 * @param orderDto
	 * @return
	 * @throws ProductException
	 * @throws OrderException
	 * @throws InventoryException
	 */
	public long createOrder(OrderDto orderDto) throws ProductException, OrderException, InventoryException {

		// save customer details
		Customer customer = customerService.createCustomer(orderDto.getCustomer());
		boolean hasStockIssues = false;

		Order order = orderRepository.saveAndFlush(new Order(customer, orderDto.getCreatedBy(), LocalDateTime.now(),
				null, null, computeTotal(orderDto.getOrderDetails()), OrderStatusEnum.NEW,
				orderDto.getAdditionalNotes(), orderDto.getPaymentMethod(), null, hasStockIssues));

		if (!ObjectUtils.isEmpty(customer) && !ObjectUtils.isEmpty(order)) {

			for (OrderDetailDto orderDetailDto : orderDto.getOrderDetails()) {

				Optional<Product> productResult = productRepository.findById(orderDetailDto.getProductId());

				if (productResult.isPresent()) {
					Product product = productResult.get();
					OrderDetail orderDetail = new OrderDetail();
					orderDetail.setOrder(order);
					orderDetail.setProduct(product);
					orderDetail.setQuantity(orderDetailDto.getQuantity());
					orderDetail.setTotal(product.getPrice() * orderDetailDto.getQuantity());
					inventoryService.commitStock(product.getParentId(), orderDetailDto.getQuantity());

					orderDetailRepository.saveAndFlush(orderDetail);
				} else
					throw new ProductException(ProductException.PRODUCT_NOT_FOUND_EXCEPTION);

			}
			inventoryService.evaluateAndUpdateInventoryIssuesForAllNewOrders();

			return order.getId();

		} else
			throw new OrderException(OrderException.INVALID_ORDER_PARAMETER_EXCEPTION);
	}

	/**
	 * compute total for each order detail
	 * 
	 * @param orderDetails
	 * @return
	 */
	public long computeTotal(List<OrderDetailDto> orderDetails) {

		long totalPrice = 0;

		for (OrderDetailDto orderDetail : orderDetails) {
			totalPrice = totalPrice + (orderDetail.getPrice() * orderDetail.getQuantity());
		}

		return totalPrice;
	}

}