package com.flexicore.order.service;

import com.flexicore.annotations.plugins.PluginInfo;
import com.flexicore.data.jsoncontainers.PaginationResponse;
import com.flexicore.model.Baseclass;
import com.flexicore.order.data.OrderRepository;
import com.flexicore.order.interfaces.IOrderApiService;
import com.flexicore.order.model.Order;
import com.flexicore.order.request.CreateOrder;
import com.flexicore.order.request.OrderFiltering;
import com.flexicore.order.request.SendOrder;
import com.flexicore.order.request.UpdateOrder;
import com.flexicore.organization.model.Organization;
import com.flexicore.organization.model.Supplier;
import com.flexicore.organization.model.SupplierApi;
import com.flexicore.security.SecurityContext;
import com.flexicore.service.PluginService;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@PluginInfo(version = 1)
public class OrderService implements com.flexicore.order.interfaces.IOrderService {

    @Inject
    @PluginInfo(version = 1)
    private OrderRepository orderRepository;

    @Inject
    private PluginService pluginService;

    @Override
    public PaginationResponse<Order> getAllOrders(OrderFiltering orderFiltering, SecurityContext securityContext) {
        List<Order> list = listAllOrders(orderFiltering, securityContext);
        long count = orderRepository.countAllOrders(orderFiltering, securityContext);
        return new PaginationResponse<>(list, orderFiltering, count);
    }

    @Override
    public List<Order> listAllOrders(OrderFiltering orderFiltering, SecurityContext securityContext) {
        return orderRepository.listAllOrders(orderFiltering, securityContext);
    }

    @Override
    public void validate(OrderFiltering orderFiltering, SecurityContext securityContext) {
        Set<String> consumingOrganizationIds = orderFiltering.getConsumingOrganizationIds();
        Map<String, Organization> organizationMap = !consumingOrganizationIds.isEmpty() ? listByIds(Organization.class, consumingOrganizationIds, securityContext).parallelStream().collect(Collectors.toMap(f -> f.getId(), f -> f)) : new HashMap<>();
        consumingOrganizationIds.removeAll(organizationMap.keySet());
        if (!consumingOrganizationIds.isEmpty()) {
            throw new BadRequestException("No Organizations with ids " + consumingOrganizationIds);
        }
        orderFiltering.setConsumingOrganizations(new ArrayList<>(organizationMap.values()));

        Set<String> supplierIds = orderFiltering.getSupplierIds();
        Map<String, Supplier> supplierMap = !supplierIds.isEmpty() ? listByIds(Supplier.class, supplierIds, securityContext).parallelStream().collect(Collectors.toMap(f -> f.getId(), f -> f)) : new HashMap<>();
        supplierIds.removeAll(supplierMap.keySet());
        if (!supplierIds.isEmpty()) {
            throw new BadRequestException("No Suppliers with ids " + supplierIds);
        }
        orderFiltering.setSuppliers(new ArrayList<>(supplierMap.values()));
    }

    @Override
    public void validate(CreateOrder createOrder, SecurityContext securityContext) {
        String consumingOrganizationId = createOrder.getConsumingOrganizationId();
        Organization consumingOrganization = consumingOrganizationId != null ? getByIdOrNull(consumingOrganizationId, Organization.class, null, securityContext) : null;
        if (consumingOrganization == null && consumingOrganizationId != null) {
            throw new BadRequestException("No Organization with id " + consumingOrganizationId);
        }
        createOrder.setConsumingOrganization(consumingOrganization);

        String supplierId = createOrder.getSupplierId();
        Supplier supplier = supplierId != null ? getByIdOrNull(supplierId, Supplier.class, null, securityContext) : null;
        if (supplier == null && supplierId != null) {
            throw new BadRequestException("No Supplier with id " + supplierId);
        }
        createOrder.setSupplier(supplier);
        int ordinal = orderRepository.getCurrentOrdinal(securityContext) + 1;
        createOrder.setOrdinal(ordinal);
    }

    @Override
    public void validate(SendOrder sendOrder, SecurityContext securityContext) {
        String orderId = sendOrder.getId();
        Order order = this.getByIdOrNull(orderId, Order.class, null, securityContext);
        if (order == null) {
            throw new BadRequestException("No Order with id " + orderId);
        }
        sendOrder.setOrder(order);
    }

    @Override
    public Order createOrder(CreateOrder createOrder, SecurityContext securityContext) {
        Order order = createOrderNoMerge(createOrder, securityContext);
        orderRepository.merge(order);
        return order;
    }

    @Override
    public Order updateOrder(UpdateOrder updateOrder, SecurityContext securityContext) {
        Order order = updateOrder.getOrder();
        if (updateOrderNoMerge(order, updateOrder)) {
            orderRepository.merge(order);
        }
        return order;
    }

    @Override
    public Order createOrderNoMerge(CreateOrder createOrder, SecurityContext securityContext) {
        Order order = Order.s().CreateUnchecked(createOrder.getName(), securityContext);
        order.Init();
        updateOrderNoMerge(order, createOrder);
        return order;
    }

    @Override
    public boolean updateOrderNoMerge(Order order, CreateOrder createOrder) {
        boolean update = false;
        if (createOrder.getName() != null && !createOrder.getName().equals(order.getName())) {
            order.setName(createOrder.getName());
            update = true;
        }
        if (createOrder.getDescription() != null && !createOrder.getDescription().equals(order.getDescription())) {
            order.setDescription(createOrder.getDescription());
            update = true;
        }
        if (createOrder.getExternalId() != null && !createOrder.getExternalId().equals(order.getExternalId())) {
            order.setExternalId(createOrder.getExternalId());
            update = true;
        }
        if (createOrder.getConsumingOrganization() != null && (order.getConsumingOrganization() == null || !createOrder.getConsumingOrganization().getId().equals(order.getConsumingOrganization().getId()))) {
            order.setConsumingOrganization(createOrder.getConsumingOrganization());
            update = true;
        }
        if (createOrder.getSupplier() != null && (order.getSupplier() == null || !createOrder.getSupplier().getId().equals(order.getSupplier().getId()))) {
            order.setSupplier(createOrder.getSupplier());
            update = true;
        }

        if (createOrder.getOrderDate() != null && (order.getOrderDate() == null || !createOrder.getOrderDate().equals(order.getOrderDate()))) {
            order.setOrderDate(createOrder.getOrderDate());
            update = true;
        }

        if (createOrder.getOrderSentDate() != null && (order.getOrderSentDate() == null || !createOrder.getOrderSentDate().equals(order.getOrderSentDate()))) {
            order.setOrderSentDate(createOrder.getOrderSentDate());
            update = true;
        }

        if (createOrder.getOrdinal() != null && createOrder.getOrdinal() != order.getOrdinal()) {
            order.setOrdinal(createOrder.getOrdinal());
            update = true;
        }
        return update;
    }

    @Override
    public Order sendOrder(SendOrder sendOrder, SecurityContext securityContext) {
        Order order = sendOrder.getOrder();
        if (order.getOrderSentDate() != null) {
            throw new BadRequestException("Order is already sent");
        }
        SupplierApi supplierApi = order.getSupplier().getSupplierApi();
        if (supplierApi != null) {
            String canonicalName = supplierApi.getImplementorCanonicalName();
            List<IOrderApiService> plugins = new ArrayList<>((Collection<IOrderApiService>) pluginService.getPlugins(IOrderApiService.class, null, null));
            try {
                Optional<IOrderApiService> orderApiService = plugins.parallelStream().filter(n -> n.IsMatchImplementorCanonicalName(canonicalName)).findFirst();
                if (!orderApiService.isPresent()) {
                    throw new BadRequestException("No OrderApiService under name " + canonicalName);
                }
                IOrderApiService.Credentials credentials = new IOrderApiService.Credentials();
                credentials.host = "81.218.245.170";
                credentials.username = "smartsell";
                credentials.password = "*********";
                orderApiService.get().sendOrder(securityContext, credentials, order);
            } catch (Exception ex) {
                throw new BadRequestException("Failed to send order " + ex.getMessage());
            } finally {
                for (IOrderApiService plugin : plugins) {
                    pluginService.cleanUpInstance(plugin);
                }
            }
        }
        order.setOrderSentDate(LocalDateTime.now());
        orderRepository.merge(order);
        return order;
    }

    public <T extends Baseclass> List<T> listByIds(Class<T> c, Set<String> ids, SecurityContext securityContext) {
        return orderRepository.listByIds(c, ids, securityContext);
    }

    public <T extends Baseclass> T getByIdOrNull(String id, Class<T> c, List<String> batchString, SecurityContext securityContext) {
        return orderRepository.getByIdOrNull(id, c, batchString, securityContext);
    }
}
