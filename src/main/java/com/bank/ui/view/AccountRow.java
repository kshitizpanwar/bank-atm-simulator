package com.bank.ui.view;

public record AccountRow(long accountNumber, String holderName, String accountType,
                         String balance, String status) {
}
