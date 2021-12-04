package org.datatransferproject.types.common.models.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import org.datatransferproject.types.common.models.ContainerResource;

import java.util.Collection;
import java.util.Objects;

/**
 * @Author Neil Chao
 * @Date 2021/9/3 12:37
 */
@JsonTypeName("OrderContainerResource")
public class OrderContainerResource extends ContainerResource {
    private final Collection<OrderModel> orders;

    @JsonCreator
    public OrderContainerResource(
            @JsonProperty("orders") Collection<OrderModel> orders) {
        this.orders = (orders == null) ? ImmutableList.of() : orders;
    }

    public Collection<OrderModel> getOrders() {
        return orders;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("orders", orders.size())
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderContainerResource that = (OrderContainerResource) o;
        return Objects.equals(getOrders(), that.getOrders());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOrders());
    }
}
