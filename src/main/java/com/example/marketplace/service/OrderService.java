package com.example.marketplace.service;

import com.example.marketplace.model.Order;
import com.example.marketplace.model.Product;
import com.example.marketplace.model.User;
import com.example.marketplace.model.Notification;
import com.example.marketplace.repository.NotificationRepository;
import com.example.marketplace.repository.OrderRepository;
import com.example.marketplace.repository.ProductRepository;
import com.example.marketplace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    public Order placeSingleOrder(String email, String productId, int quantity, String shippingAddress, String buyerName, String buyerPhone) {
        System.out.println("Placing single order for buyer: " + email + ", product: " + productId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getSellerId() == null) {
            System.out.println("Warning: Product " + productId + " has null sellerId. Trying to find user owner...");
        }

        BigDecimal totalPrice = product.getPrice().multiply(new BigDecimal(quantity));

        Order.OrderItem item = Order.OrderItem.builder()
                .productId(productId)
                .name(product.getName())
                .price(product.getPrice())
                .quantity(quantity)
                .build();

        Order order = Order.builder()
                .buyerId(email)
                .buyerName(buyerName)
                .buyerPhone(buyerPhone)
                .sellerId(product.getSellerId())
                .items(Collections.singletonList(item))
                .totalPrice(totalPrice)
                .shippingAddress(shippingAddress)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Order saved = orderRepository.save(order);
        System.out.println("Saved single order " + saved.getId() + " with sellerId " + product.getSellerId());
        return saved;
    }

    public Order acceptOrder(String orderId, String sellerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getSellerId().equals(sellerEmail)) {
            throw new RuntimeException("Unauthorized");
        }

        order.setStatus("ACCEPTED");
        order.setUpdatedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        // Notify Buyer
        notificationRepository.save(Notification.builder()
                .recipientId(order.getBuyerId())
                .senderId(sellerEmail)
                .title("Order Accepted")
                .message("Your order for " + order.getItems().get(0).getName() + " has been accepted by the seller.")
                .type("ORDER_ACCEPTED")
                .relatedId(order.getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build());

        return savedOrder;
    }

    public Order declineOrder(String orderId, String sellerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getSellerId().equals(sellerEmail)) {
            throw new RuntimeException("Unauthorized");
        }

        order.setStatus("DECLINED");
        order.setUpdatedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        // Notify Buyer
        notificationRepository.save(Notification.builder()
                .recipientId(order.getBuyerId())
                .senderId(sellerEmail)
                .title("Order Declined")
                .message("Your order for " + order.getItems().get(0).getName() + " has been declined by the seller.")
                .type("ORDER_DECLINED")
                .relatedId(order.getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build());

        return savedOrder;
    }

    public List<Order> getOrdersForSeller(String sellerEmail) {
        System.out.println("Fetching orders for seller: " + sellerEmail);
        List<Order> orders = orderRepository.findBySellerIdOrderByCreatedAtDesc(sellerEmail);
        System.out.println("Found " + orders.size() + " orders for " + sellerEmail);
        return orders;
    }

    public Order placeOrderFromWishlist(String email, String shippingAddress) {
        System.out.println("Placing wishlist order for buyer: " + email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Set<String> wishlist = user.getWishlist();
        if (wishlist == null || wishlist.isEmpty()) {
            throw new RuntimeException("Wishlist is empty");
        }

        List<Order.OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;
        String sellerId = null;

        for (String productId : wishlist) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

            if (sellerId == null) {
                sellerId = product.getSellerId();
                if (sellerId == null) {
                    // Fallback to shop id or handle error
                    System.out.println("Warning: Product " + productId + " has null sellerId");
                }
            }

            orderItems.add(Order.OrderItem.builder()
                    .productId(productId)
                    .name(product.getName())
                    .price(product.getPrice())
                    .quantity(1)
                    .build());

            totalPrice = totalPrice.add(product.getPrice());
        }

        Order order = Order.builder()
                .buyerId(email)
                .sellerId(sellerId)
                .items(orderItems)
                .totalPrice(totalPrice)
                .shippingAddress(shippingAddress)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Clear wishlist after order
        user.getWishlist().clear();
        userRepository.save(user);

        Order saved = orderRepository.save(order);
        System.out.println("Saved wishlist order " + saved.getId() + " with sellerId " + sellerId);
        return saved;
    }

    public List<Order> getMyOrders(String email) {
        return orderRepository.findByBuyerId(email);
    }
}
