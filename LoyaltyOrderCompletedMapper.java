package com.pg.sparc.mapper;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;

import com.pg.sparc.entity.CounterEntity;
import com.pg.sparc.entity.ProductEntity;
import com.pg.sparc.entity.types.*;
import com.pg.sparc.market.jp.model.ConsumerTypeEnum;
import com.pg.sparc.model.*;
import com.pg.sparc.model.c360.OrderProductDto;
import com.pg.sparc.model.c360.OrderTransactionDto;
import com.pg.sparc.model.campaign.CampaignDto;
import com.pg.sparc.repository.ProductRepository;
import com.pg.sparc.repository.TransactionRepository;
import com.pg.sparc.repository.custom.ProductCustomRepository;

import lombok.extern.slf4j.Slf4j;

@Mapper(componentModel = "spring")
@Slf4j
public abstract class LoyaltyOrderCompletedMapper {

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
    private ProductRepository productRepository;

    @Autowired
    private ProductCustomRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    public OrderTransactionDto toDto(TransactionModel transaction, CounterEntity counter, String marketCode) {
        log.debug("Mapping TransactionModel to OrderTransactionDto for transactionId: {}",
                  Optional.ofNullable(transaction).map(TransactionModel::getId).orElse("UNKNOWN"));

        if (transaction == null) {
            log.warn("TransactionModel is null. Returning empty OrderTransactionDto.");
            return new OrderTransactionDto();
        }

        OrderTransactionDto completed = new OrderTransactionDto();

        if (counter != null) {
            completed.setCounterId(counter.getCode());
            completed.setStoreCode(counter.getCode());
            completed.setCounterNameMarket(counter.getNameMarket());
            completed.setCounterNameEn(counter.getNameEn());
            completed.setCounterZipCode(counter.getZip());
        }

        completed.setTransactionId(transaction.getId());
        completed.setBcId(transaction.getBcId());
        completed.setVipId(transaction.getMemberId());
        completed.setMemberId(transaction.getMemberId());

        if (transaction.getPurchaseDate() != null) {
            completed.setPurchaseDate(transaction.getPurchaseDate().withZoneSameInstant(ZoneId.systemDefault()).format(dateTimeFormat));
            completed.setPurchaseUpdateDate(completed.getPurchaseDate());
        }
        if (transaction.getTransactionDateTime() != null) {
            completed.setTransactionDate(transaction.getTransactionDateTime().withZoneSameInstant(ZoneId.systemDefault()).format(dateFormat));
        }

        Optional.ofNullable(transaction.getTransactionSalePlace()).ifPresent(v -> completed.setTransactionSalePlace(v.getKey()));
        Optional.ofNullable(transaction.getTransactionSaleMode()).ifPresent(v -> completed.setTransactionSaleMode(v.getKey()));
        Optional.ofNullable(transaction.getTransactionSource()).ifPresent(v -> completed.setTransactionSource(v.getKey()));
        Optional.ofNullable(transaction.getTransactionType()).ifPresent(v -> completed.setTransactionType(v.getKey()));
        Optional.ofNullable(transaction.getConsumerType()).ifPresent(v -> completed.setConsumerType(v.getKey()));

        completed.setTransactionRetailAmount(castToBigDecimal(transaction.getTransactionRetailAmount()));
        completed.setTransactionStoreAmount(castToBigDecimal(transaction.getTransactionStoreAmount()));
        completed.setTransactionRealAmount(castToBigDecimal(transaction.getTransactionRealAmount()));
        completed.setTransactionQty(Optional.ofNullable(transaction.getTransactionQty()).orElse(0));  // Default: 0
        completed.setIsByCounter(BooleanUtils.isTrue(transaction.getIsByCounter()) ? 1 : 0);
        completed.setTaxRate(castToBigDecimal(transaction.getTaxRate()));
        completed.setTransactionNote(Optional.ofNullable(transaction.getTransactionNote()).orElse("N/A"));
        completed.setProcessFlag(transaction.getProcessFlag());
        completed.setTransactionDiscountedAmount(castToBigDecimal(transaction.getTransactionDiscountedAmount()));
        completed.setTransactionPayAmount(castToBigDecimal(transaction.getTransactionPayAmount()));

        completed.setMarketCode(marketCode);
        completed.setSpaBrand(spaBrand);
        completed.setSpaRegion(spaRegion);
        completed.setSpaChannel(Optional.ofNullable(transaction.getCounterId()).map(id -> id.equals(rakutenCounter) ? online : spaChannel).orElse(spaChannel));
        completed.setIsSendToLoyalty(true);

        setOrderTransactionDto(completed, transaction, marketCode);
        return completed;
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

    public String mapConsumerState(String transactionConsumerType) {
        return switch (transactionConsumerType) {
            case "EXISTING" -> CampaignUserType.EU.toString();
            case "FIRSTREPEATER" -> CampaignUserType.RU.toString();
            case "NEWUSER" -> CampaignUserType.NU.toString();
            case "PRECLUB" -> CampaignUserType.PU.toString();
            default -> CampaignUserType.NONE.toString();
        };
    }
}
