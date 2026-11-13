package com.newtube.mobile.accounts;

import java.util.ArrayList;
import java.util.List;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

/** Cycles through the account service's current list, without storing a separate order. */
public final class AccountCycle {
    private AccountCycle() {
    }

    public static Account next(List<Account> accounts) {
        List<Account> available = new ArrayList<>();
        int selected = -1;
        if (accounts != null) {
            for (Account account : accounts) {
                if (account != null && !account.isEmpty()) {
                    if (account.isSelected()) {
                        selected = available.size();
                    }
                    available.add(account);
                }
            }
        }
        return available.isEmpty() ? null : available.get((selected + 1) % available.size());
    }
}
