/** Contratos da API REST (ver DTOs em backend/core-api e backend/asset-management). */

export interface User {
    id: string;
    name: string;
    email: string;
}

export interface LoginRequest {
    email: string;
    password: string;
}

export interface RegisterRequest extends LoginRequest {
    name: string;
}

export type AssetType = 'STOCK' | 'CRYPTO';
export type TransactionType = 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAW';
export type FiatMovementType = Extract<TransactionType, 'DEPOSIT' | 'WITHDRAW'>;

export interface Position {
    symbol: string;
    name: string;
    assetType: AssetType;
    quantity: number;
    averagePrice: number;
}

export interface WalletSummary {
    fiatBalance: number;
    positions: Position[];
}

/** Em DEPOSIT e WITHDRAW, `symbol`, `quantity` e `unitPrice` vêm nulos. */
export interface Transaction {
    id: string;
    type: TransactionType;
    symbol: string | null;
    quantity: number | null;
    unitPrice: number | null;
    fee: number;
    totalAmount: number;
    timestamp: string;
}

/** `autoDeposit` só vem preenchido quando a compra precisou gerar um depósito automático. */
export interface OperationResponse {
    transaction: Transaction;
    autoDeposit: Transaction | null;
    fiatBalance: number;
}

export interface BuyRequest {
    symbol: string;
    name?: string;
    assetType: AssetType;
    quantity: number;
    unitPrice: number;
    fee: number;
    autoDeposit: boolean;
}

export interface SellRequest {
    symbol: string;
    quantity: number;
    unitPrice: number;
    fee: number;
}

export interface FiatMovementRequest {
    type: FiatMovementType;
    amount: number;
}
