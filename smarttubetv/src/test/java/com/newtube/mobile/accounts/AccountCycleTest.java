package com.newtube.mobile.accounts;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class AccountCycleTest {
    private final FakeAccount a = new FakeAccount("A");
    private final FakeAccount b = new FakeAccount("B");
    private final FakeAccount c = new FakeAccount("C");

    @Test public void moreThanTwoAccountsFollowTheCurrentSavedList() {
        a.selected = true;
        assertSame(b, AccountCycle.next(Arrays.asList(a, b, c)));
        a.selected = false;
        b.selected = true;
        assertSame(c, AccountCycle.next(Arrays.asList(a, b, c)));
    }

    @Test public void lastAccountWrapsToFirstWithoutSigningOut() {
        c.selected = true;
        assertSame(a, AccountCycle.next(Arrays.asList(a, b, c)));
    }

    @Test public void signedOutStartsWithTheFirstSavedAccount() {
        assertSame(a, AccountCycle.next(Arrays.asList(a, b, c)));
    }

    @Test public void changedBackendOrderIsUsedImmediately() {
        a.selected = true;
        assertSame(b, AccountCycle.next(Arrays.asList(a, b, c)));
        assertSame(c, AccountCycle.next(Arrays.asList(b, a, c)));
    }

    @Test public void addedAndRemovedAccountsNeedNoOrderingPreference() {
        b.selected = true;
        assertSame(a, AccountCycle.next(Arrays.asList(a, b)));
        assertSame(c, AccountCycle.next(Arrays.asList(a, b, c)));
        assertSame(a, AccountCycle.next(Arrays.asList(a, b)));
    }

    @Test public void anEmptyStoreHasNoNextAccount() {
        assertNull(AccountCycle.next(null));
        assertNull(AccountCycle.next(Collections.emptyList()));
    }

    @Test public void incompleteSignInRecordsAndNullsAreSkipped() {
        FakeAccount placeholder = new FakeAccount(null);
        placeholder.selected = true;
        assertSame(a, AccountCycle.next(Arrays.asList(null, placeholder, a, b)));
    }

    @Test public void oneSelectedAccountIsReturnedWithoutASecondSelection() {
        a.selected = true;
        assertSame(a, AccountCycle.next(Collections.singletonList(a)));
    }

    @Test public void twoAccountsAlternateEvenWhenLegacyIntegerIdsMatch() {
        a.selected = true;
        assertSame(b, AccountCycle.next(Arrays.asList(a, b)));
        a.selected = false;
        b.selected = true;
        assertSame(a, AccountCycle.next(Arrays.asList(a, b)));
    }

    private static final class FakeAccount implements Account {
        private final String name;
        boolean selected;
        FakeAccount(String name) { this.name = name; }
        @Override public int getId() { return 0; }
        @Override public String getName() { return name; }
        @Override public String getEmail() { return null; }
        @Override public String getAvatarImageUrl() { return null; }
        @Override public boolean isSelected() { return selected; }
        @Override public boolean isEmpty() { return name == null; }
    }
}
