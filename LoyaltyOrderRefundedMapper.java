package com.pg.sparc.mapper;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;

import com.pg.sparc.entity.CounterEntity;
import com.pg.sparc.entity.types.*;
import com.pg.sparc.market.jp.model.ConsumerTypeEnum;
import com.pg.sparc.model.*;
import com.pg.sparc.model.c360.OrderTransactionDto;
import com.pg.sparc.model.campaign.CampaignDto;
import com.pg.sparc.repository.TransactionRepository;

import lombok.extern.slf4j.Slf4j;

@Mapper(componentModel = "spring")
@Slf4j
public abstract class LoyaltyOrderRefundedMapper {
    
    private static final String MARKET_CODE = "100";
    private static final String DEFAULT_VALUE = "0";
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${spa.brand:SKII}")
    private String spaBrand;

    @Value("${spa.region:HK}")
    private String spaRegion;

    @Value("${spa.channel:COUNTER}")
    private String spaChannel;

    @Value("${spa.rakuten.channel:ONLINE}")
    private String online;

    @Value("${sparc.rakuten.counter}")
    private String rakutenCounter;

    @Autowired
    private TransactionRepository transactionRepository;

    public OrderTransactionDto toDto(TransactionModel returnTransaction,
                                     TransactionModel purchaseTransaction,
                                     TransactionModel originalTransaction,
                                     CounterEntity counter,
                                     final boolean isSendToC360, 
                                     final boolean isSendToLoyalty, 
                                     String marketCode) {
        log.debug("Mapping TransactionModel to OrderTransactionDto for transactionId: {}", 
                  Optional.ofNullable(originalTransaction).map(TransactionModel::getId).orElse("UNKNOWN"));

        if (originalTransaction == null) {
            log.warn("Original transaction is null. Returning empty OrderTransactionDto.");
            return new OrderTransactionDto();
        }

        OrderTransactionDto refunded = new OrderTransactionDto();

        if (counter != null) {
            refunded.setCounterId(counter.getId());
            refunded.setStoreCode(counter.getCode());
            refunded.setCounterNameMarket(counter.getNameMarket());
            refunded.setCounterNameEn(counter.getNameEn());
            refunded.setCounterZipCode(counter.getZip());
        }

        refunded.setTransactionId(originalTransaction.getId());
        refunded.setBcId(originalTransaction.getBcId());
        refunded.setVipId(originalTransaction.getMemberId());
        refunded.setMemberId(originalTransaction.getMemberId());
        refunded.setPurchaseUpdateDate(ZonedDateTime.now(ZoneOffset.UTC).format(dateTimeFormat));

        refunded.setTransactionDate(Optional.ofNullable(originalTransaction.getTransactionDateTime())
                                            .map(date -> date.withZoneSameInstant(ZoneId.systemDefault()).format(dateFormat))
                                            .orElse("N/A"));

        Optional.ofNullable(originalTransaction.getTransactionSalePlace()).ifPresent(v -> refunded.setTransactionSalePlace(v.getKey()));
        Optional.ofNullable(originalTransaction.getTransactionSaleMode()).ifPresent(v -> refunded.setTransactionSaleMode(v.getKey()));
        Optional.ofNullable(originalTransaction.getTransactionSource()).ifPresent(v -> refunded.setTransactionSource(v.getKey()));
        Optional.ofNullable(originalTransaction.getTransactionType()).ifPresent(v -> refunded.setTransactionType(v.getKey()));

        refunded.setConsumerType(Optional.ofNullable(returnTransaction)
                                         .map(TransactionModel::getConsumerType)
                                         .map(ConsumerType::getKey)
                                         .orElse(ConsumerType.FREE_USER.getKey()));

        refunded.setIsByCounter(Boolean.TRUE.equals(originalTransaction.getIsByCounter()) ? 1 : 0);
        refunded.setTaxRate(castToBigDecimal(originalTransaction.getTaxRate()));
        refunded.setTransactionNote(Optional.ofNullable(originalTransaction.getTransactionNote()).orElse("N/A"));
        refunded.setProcessFlag(originalTransaction.getProcessFlag());
        refunded.setTransactionDiscountedAmount(castToBigDecimal(originalTransaction.getTransactionDiscountedAmount()));
        refunded.setTransactionPayAmount(castToBigDecimal(originalTransaction.getTransactionPayAmount()));

        refunded.setMarketCode(MARKET_CODE);
        refunded.setSpaBrand(spaBrand);
        refunded.setSpaRegion(spaRegion);
        refunded.setSpaChannel(Optional.ofNullable(returnTransaction)
                                       .map(rt -> rt.getCounterId().equals(rakutenCounter) ? online : spaChannel)
                                       .orElse(spaChannel));
        
        refunded.setEventType(BusinessType.ORDER_REFUND_DATA.getCode());
        refunded.setIsSendToLoyalty(true);

        if (returnTransaction != null) {
            setOrderTransactionDto(refunded, returnTransaction, marketCode);
        }

        return refunded;
    }

    private void setOrderTransactionDto(OrderTransactionDto orderTransactionDto, TransactionModel transaction, String marketCode) {
        BigDecimal grossAmount = transaction.getTransactionProducts().stream()
            .map(product -> Optional.ofNullable(product.getRetailPrice()).orElse(BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(Optional.ofNullable(product.getQty()).orElse(0))))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        orderTransactionDto.setGrossAmount(grossAmount);

        String transactionConsumerType = Optional.ofNullable(transaction.getMemberId())
                                                 .filter(StringUtils::isNotBlank)
                                                 .map(memberId -> transactionRepository.getCurrentMemberConsumerType(memberId, marketCode))
                                                 .orElse(ConsumerTypeEnum.PRECLUB.getValue());

        orderTransactionDto.setConsumerState(mapConsumerState(transactionConsumerType));
        orderTransactionDto.setOrderUpdateTime(Optional.ofNullable(transaction.getLastModifiedDate()).map(Object::toString).orElse("N/A"));
    }

    public String mapConsumerState(String transactionConsumerType) {
        return switch (transactionConsumerType) {
            case "EXISTING" -> CampaignUserType.EU.toString();
            case "FIRSTREPEATER" -> CampaignUserType.RU.toString();
            case "NEWUSER" -> CampaignUserType.NU.toString();
            case "PRECLUB" -> CampaignUserType.PU.toString();
            default -> CampaignUserType.NONE.toString();
        };
    }

    private BigDecimal castToBigDecimal(String number) {
        if (StringUtils.isBlank(number)) return BigDecimal.ZERO;
        try {
            return new BigDecimal(number);
        } catch (NumberFormatException e) {
            log.warn("Invalid number format: {}. Returning BigDecimal.ZERO", number, e);
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal castToBigDecimal(Double number) {
        return Optional.ofNullable(number).map(BigDecimal::valueOf).orElse(BigDecimal.ZERO);
    }
}
