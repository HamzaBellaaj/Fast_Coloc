package tn.fastcoloc.profiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.onboarding.OnboardingService;
import tn.fastcoloc.security.AccountAuthorizationService;

@Service
public class UserBlockService {
    public record BlockedUser(UUID id, String displayName, OffsetDateTime createdAt) {}
    public record BlockStatus(boolean blocked) {}
    public record MemberProfile(String displayName) {}

    private final UserBlockRepository blocks;
    private final AccountAuthorizationService access;
    private final OnboardingService accounts;

    UserBlockService(UserBlockRepository blocks, AccountAuthorizationService access, OnboardingService accounts) {
        this.blocks = blocks;
        this.access = access;
        this.accounts = accounts;
    }

    @Transactional
    public void setBlock(UUID actor, UUID target, boolean block) {
        accounts.bindAndLock(actor);
        checkTarget(actor, target);
        blocks.lockPair(actor, target);
        if (block) {
            blocks.add(actor, target);
        } else {
            blocks.remove(actor, target);
        }
    }

    @Transactional(readOnly = true)
    public List<BlockedUser> list(UUID actor) {
        access.role(actor);
        return blocks.blocked(actor);
    }

    @Transactional(readOnly = true)
    public BlockStatus status(UUID actor, UUID target) {
        access.role(actor);
        checkTarget(actor, target);
        return new BlockStatus(blocks.ownsBlock(actor, target));
    }

    @Transactional(readOnly = true)
    public MemberProfile profile(UUID actor, UUID target) {
        access.role(actor);
        var member = checkTarget(actor, target);
        if (!member.listed() && !blocks.ownsBlock(actor, target)) throw missing();
        return new MemberProfile(member.displayName());
    }

    private UserBlockRepository.Member checkTarget(UUID actor, UUID target) {
        if (actor.equals(target))
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_BLOCK", "Tu ne peux pas te bloquer.");
        var member = blocks.member(target);
        if (member == null) throw missing();
        return member;
    }

    private ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Utilisateur introuvable.");
    }
}
