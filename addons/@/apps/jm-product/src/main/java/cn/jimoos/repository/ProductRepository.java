package cn.jimoos.repository;

import cn.jimoos.common.exception.BussException;
import cn.jimoos.dao.*;
import cn.jimoos.entity.ProductEntity;
import cn.jimoos.error.ProductError;
import cn.jimoos.model.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author :keepcleargas
 * @date :2021-03-30 20:44.
 */
@Repository
public class ProductRepository {
    @Resource
    ProductMapper productMapper;
    @Resource
    ProductCategoryMapper productCategoryMapper;
    @Resource
    ProductSkuMapper productSkuMapper;
    @Resource
    RProductTagMapper rProductTagMapper;
    @Resource
    ProductTagMapper productTagMapper;
    @Resource
    ProductSkuAttrMapMapper productSkuAttrMapMapper;

    /**
     * 保存 ProductEntity信息
     *
     * @param productEntity product entity
     */
    public void save(ProductEntity productEntity) {
        if (productEntity.getId() != null && productEntity.getId() > 0) {
            productMapper.updateByPrimaryKey(productEntity);

            //移除 tag 关联
            rProductTagMapper.deleteByProductId(productEntity.getId());
            List<RProductTag> rProductTags = productEntity.getRProductTagInputs();
            if (!CollectionUtils.isEmpty(rProductTags)) {
                rProductTagMapper.batchInsert(rProductTags);
            }
        } else {
            productMapper.insert(productEntity);

            List<RProductTag> rProductTags = productEntity.getRProductTagInputs();
            if (!CollectionUtils.isEmpty(rProductTags)) {
                rProductTags = rProductTags.stream().peek(rProductTag -> rProductTag.setProductId(productEntity.getId())).collect(Collectors.toList());
            }
            rProductTagMapper.batchInsert(rProductTags);
        }
    }

    /**
     * 查找 product 对象
     *
     * @param id product id
     * @return ProductEntity
     * @throws BussException ProductError.PRODUCT_NOT_EXIST
     */
    public ProductEntity byId(Long id) throws BussException {
        Product product = productMapper.selectByPrimaryKey(id);
        if (product == null) {
            throw new BussException(ProductError.PRODUCT_NOT_EXIST);
        }
        return wrapper(product, false);
    }

    /**
     * Product的 entity wrapper方法
     *
     * @param product  product
     * @param skipRepo skip repo inject
     */
    private ProductEntity wrapper(Product product, boolean skipRepo) {
        if (product != null) {
            ProductEntity productEntity;
            if (skipRepo) {
                productEntity = new ProductEntity();
            } else {
                productEntity = new ProductEntity(this);
            }
            BeanUtils.copyProperties(product, productEntity);
            return productEntity;
        }
        return null;
    }

    /**
     * 保存 SKUs 信息,删除原有的 再更新。
     *
     * @param productEntity Product Entity
     */
    public void saveSkus(ProductEntity productEntity) {
        if (productEntity != null) {
            productSkuMapper.updateDeletedByProductId(Boolean.TRUE, productEntity.getId());
            List<ProductEntity.SkuEntity> skuEntities = productEntity.getProductSkuInputs();
            if (CollectionUtils.isEmpty(skuEntities)) {
                return;
            }
            productSkuMapper.batchInsert(skuEntities.stream().map(ProductSku.class::cast).collect(Collectors.toList()));

            List<ProductSku> productSkus = productEntity.getProductSkus();
            Map<String, ProductSku> idToProductSkuMap = productSkus.stream().collect(Collectors.toMap(ProductSku::getAttrValueIds, Function.identity()));

            List<ProductSkuAttrMap> productSkuAttrMaps = new ArrayList<>();

            skuEntities.forEach(skuEntity -> {
                ProductSku productSku = idToProductSkuMap.get(skuEntity.getBindAttrValueIds());
                //批量更新 sku 下的 attr map
                if (productSku != null) {
                    List<ProductSkuAttrMap> skuAttrMaps = skuEntity.getSkuAttrMaps();
                    productSkuAttrMaps.addAll(skuAttrMaps.stream().peek(skuAttr -> skuAttr.setSkuId(productSku.getId())).collect(Collectors.toList()));
                }
            });

            if (!CollectionUtils.isEmpty(productSkuAttrMaps)) {
                productSkuAttrMapMapper.batchInsert(productSkuAttrMaps);
            }
        }
    }

    /**
     * find tags by product Id
     *
     * @param productId product Id
     * @return List<ProductTag>
     */
    public List<ProductTag> findTagsByProductId(Long productId) {
        List<RProductTag> rProductTags = rProductTagMapper.findByProductId(productId);

        if (CollectionUtils.isEmpty(rProductTags)) {
            return new ArrayList<>();
        }

        List<Long> tagIds = rProductTags.stream().map(RProductTag::getTagId).collect(Collectors.toList());
        return productTagMapper.findByIdIn(tagIds);
    }

    /**
     * find category by cate id
     *
     * @param cateId category id
     * @return ProductCategory
     */
    public ProductCategory findCategoryByCateId(Long cateId) {
        return productCategoryMapper.selectByPrimaryKey(cateId);
    }

    /**
     * 查询商品的 SKU 列表
     *
     * @param id product id
     * @return List<ProductSku>
     */
    public List<ProductSku> findSkusById(Long id) {
        return productSkuMapper.findByProductId(id);
    }

    /**
     * 更新某个 SKU 的信息，无法修改 attr map
     *
     * @param skuEntity sku Entity
     */
    public void updateOneSku(ProductEntity.SkuEntity skuEntity) {
        ProductSku productSku = productSkuMapper.selectByPrimaryKey(skuEntity.getId());

        if (productSku != null) {
            productSku.setCover(skuEntity.getCover());
            productSku.setPrice(skuEntity.getPrice());
            productSku.setShowPrice(skuEntity.getShowPrice());
            productSku.setUpdateAt(System.currentTimeMillis());
            productSkuMapper.updateByPrimaryKey(productSku);
        }
    }
}
