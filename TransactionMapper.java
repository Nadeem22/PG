package com.pg.sparc.mapper;

import com.pg.sparc.entity.*;
import com.pg.sparc.model.*;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TransactionMapper {

    @Autowired
    private FileStorageToUrlMapper fileStorageToUrlMapper;

    public TransactionModel entityToModel(TransactionEntity entity) {
        log.debug("Mapping TransactionEntity to TransactionModel for transactionId: {}", 
                  Optional.ofNullable(entity).map(TransactionEntity::getId).orElse("UNKNOWN"));

        if (entity == null) {
            log.warn("TransactionEntity is null. Returning empty TransactionModel.");
            return new TransactionModel();
        }

        TransactionModel transactionModel = new TransactionModel();

        transactionModel.setCounterId(getCounterId(entity));
        transactionModel.setReceiptImageId(Optional.ofNullable(entity.getReceiptImage())
                                                   .map(fileStorageToUrlMapper::toReceiptUrl)
                                                   .orElse("N/A"));
        transactionModel.setTransactionExtType(entity.getExtType());
        transactionModel.setBcId(entity.getBcId());
        transactionModel.setConsumerType(entity.getConsumerType());
        transactionModel.setCreatedBy(entity.getCreatedBy());
        transactionModel.setCreatedDate(entity.getCreatedDate());
        transactionModel.setExtensionData(Optional.ofNullable(entity.getExtensionData()).orElse(Collections.emptyMap()));
        transactionModel.setId(entity.getId());
        transactionModel.setIsByCounter(entity.getIsByCounter());
        transactionModel.setIsShareBc(entity.getIsShareBc());
        transactionModel.setLastModifiedBy(entity.getLastModifiedBy());
        transactionModel.setLastModifiedDate(entity.getLastModifiedDate());
        transactionModel.setMemberId(entity.getMemberId());
        transactionModel.setPerfTransactionType(entity.getPerfTransactionType());
        transactionModel.setProcessFlag(entity.getProcessFlag());
        transactionModel.setPurchaseDate(entity.getPurchaseDate());
        transactionModel.setSessionId(entity.getSessionId());
        transactionModel.setTaxRate(Optional.ofNullable(entity.getTaxRate()).orElse(0.0));
        transactionModel.setTransactionCampaigns(mapCampaignEntities(entity.getTransactionCampaigns()));
        transactionModel.setTransactionDateTime(entity.getTransactionDateTime());
        transactionModel.setTransactionDiscountedAmount(Optional.ofNullable(entity.getTransactionDiscountedAmount()).orElse(0.0));
        transactionModel.setTransactionGifts(mapGiftEntities(entity.getTransactionGifts()));
        transactionModel.setTransactionNote(Optional.ofNullable(entity.getTransactionNote()).orElse("N/A"));
        transactionModel.setTransactionPayAmount(Optional.ofNullable(entity.getTransactionPayAmount()).orElse(0.0));
        transactionModel.setTransactionProducts(mapProductEntities(entity.getTransactionProducts()));
        transactionModel.setTransactionQty(Optional.ofNullable(entity.getTransactionQty()).orElse(0));
        transactionModel.setTransactionRealAmount(Optional.ofNullable(entity.getTransactionRealAmount()).orElse(0.0));
        transactionModel.setTransactionRetailAmount(Optional.ofNullable(entity.getTransactionRetailAmount()).orElse(0.0));
        transactionModel.setTransactionSaleMode(entity.getTransactionSaleMode());
        transactionModel.setTransactionSalePlace(entity.getTransactionSalePlace());
        transactionModel.setTransactionServices(mapServiceEntities(entity.getTransactionServices()));
        transactionModel.setTransactionSource(entity.getTransactionSource());
        transactionModel.setTransactionStoreAmount(Optional.ofNullable(entity.getTransactionStoreAmount()).orElse(0.0));
        transactionModel.setTransactionType(entity.getTransactionType());
        transactionModel.setVipCodeBak(entity.getVipCodeBak());

        transactionModel.setProperties(Optional.ofNullable(entity.getPropertyItems()).orElse(Collections.emptyMap()));

        return transactionModel;
    }

    private String getCounterId(TransactionEntity transactionEntity) {
        return Optional.ofNullable(transactionEntity)
                       .map(TransactionEntity::getCounter)
                       .map(CounterEntity::getId)
                       .orElse("UNKNOWN");
    }

    private List<TransactionCampaignModel> mapCampaignEntities(List<TransactionCampaignEntity> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::mapCampaignEntity).collect(Collectors.toList());
    }

    private TransactionCampaignModel mapCampaignEntity(TransactionCampaignEntity entity) {
        if (entity == null) return null;
        
        TransactionCampaignModel model = new TransactionCampaignModel();
        model.setCampaignId(entity.getCampaignId());
        model.setExtensionData(Optional.ofNullable(entity.getExtensionData()).orElse(Collections.emptyMap()));
        model.setGifts(mapCampaignGiftEntities(entity.getGifts()));
        model.setQty(Optional.ofNullable(entity.getQty()).orElse(0));

        return model;
    }

    private List<TransactionCampaignGiftModel> mapCampaignGiftEntities(List<TransactionCampaignGiftEntity> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::mapCampaignGiftEntity).collect(Collectors.toList());
    }

    private TransactionCampaignGiftModel mapCampaignGiftEntity(TransactionCampaignGiftEntity entity) {
        if (entity == null) return null;
        
        TransactionCampaignGiftModel model = new TransactionCampaignGiftModel();
        model.setExtensionData(Optional.ofNullable(entity.getExtensionData()).orElse(Collections.emptyMap()));
        model.setQty(Optional.ofNullable(entity.getQty()).orElse(0));

        return model;
    }

    private List<TransactionGiftModel> mapGiftEntities(List<TransactionGiftEntity> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::mapGiftEntity).collect(Collectors.toList());
    }

    private TransactionGiftModel mapGiftEntity(TransactionGiftEntity entity) {
        if (entity == null) return null;
        
        TransactionGiftModel model = new TransactionGiftModel();
        model.setExtensionData(Optional.ofNullable(entity.getExtensionData()).orElse(Collections.emptyMap()));
        model.setQty(Optional.ofNullable(entity.getQty()).orElse(0));

        return model;
    }

    private List<TransactionProductModel> mapProductEntities(List<TransactionProductEntity> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::mapProductEntity).collect(Collectors.toList());
    }

    private TransactionProductModel mapProductEntity(TransactionProductEntity entity) {
        if (entity == null) return null;
        
        TransactionProductModel model = new TransactionProductModel();
        model.setActionId(entity.getActionId());
        model.setExtensionData(Optional.ofNullable(entity.getExtensionData()).orElse(Collections.emptyMap()));
        model.setExtraDiscountRate(Optional.ofNullable(entity.getExtraDiscountRate()).orElse(0.0));
        model.setQty(Optional.ofNullable(entity.getQty()).orElse(0));
        model.setRealPrice(Optional.ofNullable(entity.getRealPrice()).orElse(0.0));
        model.setRetailPrice(Optional.ofNullable(entity.getRetailPrice()).orElse(0.0));
        model.setStorePrice(Optional.ofNullable(entity.getStorePrice()).orElse(0.0));

        return model;
    }

    private List<TransactionServiceModel> mapServiceEntities(List<TransactionServiceEntity> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::mapServiceEntity).collect(Collectors.toList());
    }

    private TransactionServiceModel mapServiceEntity(TransactionServiceEntity entity) {
        if (entity == null) return null;
        
        TransactionServiceModel model = new TransactionServiceModel();
        model.setExtensionData(Optional.ofNullable(entity.getExtensionData()).orElse(Collections.emptyMap()));
        model.setQty(Optional.ofNullable(entity.getQty()).orElse(0));
        model.setServiceId(entity.getServiceId());

        return model;
    }
}
