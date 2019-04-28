package cn.caohongliang.mybatis.example.dao;

import java.util.Optional;

/**
 * 基本的Mapper，由自动生成的mapper继承
 *
 * @author caohongliang
 */
public interface BaseDao<ENTITY, EXAMPLE, ID> extends BaseNotPrimaryKeyDao<ENTITY, EXAMPLE> {

    /**
     * 根据主键删除对应的记录
     *
     * @param id id
     * @return int
     */
    int deleteByPrimaryKey(ID id);

    /**
     * 根据主键查询
     *
     * @param id id
     * @return UmUser
     */
    Optional<ENTITY> selectByPrimaryKey(ID id);

    /**
     * 根据主键更新所有非空字段
     *
     * @param record record
     * @return int
     */
    int updateByPrimaryKeySelective(ENTITY record);

    /**
     * 根据主键更新所有字段
     *
     * @param record record
     * @return int
     */
    int updateByPrimaryKey(ENTITY record);
}