package com.uptimecrew.expense.readmodel;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MerchantReadModelRepository extends MongoRepository<MerchantReadModel, String> {

    List<MerchantReadModel> findByMccCode(String mccCode);
}
