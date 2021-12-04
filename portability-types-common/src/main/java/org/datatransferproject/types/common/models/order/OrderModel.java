package org.datatransferproject.types.common.models.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.datatransferproject.types.common.models.photos.PhotoModel;

import java.util.Date;

/**
 * @Author Neil Chao
 * @Date 2021/9/3 12:38
 */
public class OrderModel {

    private String serial;
    private String sellerName;
    private String buyerName;
    private Date dealTime;
    private String item;
    private String description;
    private Double turnover;

    @JsonCreator
    public OrderModel(
            @JsonProperty("serial") String serial,
            @JsonProperty("sellerName") String sellerName,
            @JsonProperty("buyerName") String buyerName,
            @JsonProperty("dealTime") Date dealTime,
            @JsonProperty("item") String item,
            @JsonProperty("description") String description,
            @JsonProperty("turnover") Double turnover) {
        if(serial == null || serial.isEmpty()) {
            throw new IllegalArgumentException("serial must be set");
        }
        this.serial = serial;
        this.sellerName = sellerName;
        this.buyerName = buyerName;
        this.dealTime = dealTime;
        this.item = item;
        this.description = description;
        this.turnover = turnover;
    }

    public String getSerial() {
        return serial;
    }

    public String getSellerName() {
        return sellerName;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public Date getDealTime() {
        return dealTime;
    }

    public String getItem() {
        return item;
    }

    public String getDescription() {
        return description;
    }

    public Double getTurnover() {
        return turnover;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("serial", serial)
                .add("sellerName", sellerName)
                .add("buyerName", buyerName)
                .add("item", item)
                .add("turnover", turnover)
                .add("description", description)
                .add("dealTime", dealTime)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderModel that = (OrderModel) o;
        return Objects.equal(getSerial(), that.getSerial()) &&
                Objects.equal(getSellerName(), that.getSellerName()) &&
                Objects.equal(getBuyerName(), that.getBuyerName()) &&
                Objects.equal(getItem(), that.getItem()) &&
                Objects.equal(getTurnover(), that.getTurnover()) &&
                Objects.equal(getDealTime(), that.getDealTime());
    }

    @Override
    public int hashCode() {
        return this.serial.hashCode();
    }
}
