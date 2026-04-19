package com.btree.infrastructure.user.persistence;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.gateway.AddressGateway;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import com.btree.infrastructure.user.entity.AddressJpaEntity;
import com.btree.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Transactional
public class AddressPostgresGateway implements AddressGateway {

    private final AddressJpaRepository addressJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public AddressPostgresGateway(
            final AddressJpaRepository addressJpaRepository,
            final UserJpaRepository userJpaRepository
    ) {
        this.addressJpaRepository = addressJpaRepository;
        this.userJpaRepository    = userJpaRepository;
    }

    @Override
    public Address save(final Address address) {
        final var userEntity = userJpaRepository.findById(address.getUserId().getValue())
                .orElseThrow(() -> NotFoundException.with("User not found: " + address.getUserId().getValue()));

        return addressJpaRepository
                .save(AddressJpaEntity.from(address, userEntity))
                .toAggregate();
    }

    @Override
    public Address update(final Address address) {
        final var entity = addressJpaRepository.findById(address.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(Address.class, address.getId().getValue()));

        entity.updateFrom(address);
        return addressJpaRepository.save(entity).toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Address> findById(final AddressId id) {
        return addressJpaRepository.findById(id.getValue())
                .map(AddressJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Address> findByUserId(final UserId userId) {
        return addressJpaRepository.findAllActiveByUserId(userId.getValue())
                .stream()
                .map(AddressJpaEntity::toAggregate)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveByUserId(final UserId userId) {
        return addressJpaRepository.countActiveByUserId(userId.getValue());
    }

    @Override
    public void clearDefaultByUserId(final UserId userId) {
        addressJpaRepository.clearDefaultByUserId(userId.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveByUserIdExcluding(final UserId userId, final AddressId excludeId) {
        return addressJpaRepository.countActiveByUserIdExcluding(
                userId.getValue(),
                excludeId.getValue()
        );
    }
}
