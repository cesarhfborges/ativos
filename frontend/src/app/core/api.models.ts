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
export type TransactionType = 'BUY' | 'SELL';

export interface Position {
    symbol: string;
    name: string;
    assetType: AssetType;
    quantity: number;
    averagePrice: number;
}

/** Sem cotação, `currentPrice` é nulo e o valor de mercado usa o preço médio, com lucro zero. */
export interface DashboardPosition extends Position {
    currentPrice: number | null;
    investedValue: number;
    marketValue: number;
    profitLoss: number;
    profitLossPercent: number;
    quoteUpdatedAt: string | null;
}

export interface DashboardOverview {
    investedValue: number;
    marketValue: number;
    profitLoss: number;
    profitLossPercent: number;
    positionsWithoutQuote: number;
    quotesUpdatedAt: string | null;
}

export interface PortfolioHistoryPoint {
    time: string;
    investedBalance: number;
    totalBalance: number;
}

export interface Transaction {
    id: string;
    type: TransactionType;
    symbol: string;
    quantity: number;
    unitPrice: number;
    fee: number;
    totalAmount: number;
    timestamp: string;
}

export interface BuyRequest {
    symbol: string;
    name?: string;
    assetType: AssetType;
    quantity: number;
    unitPrice: number;
    fee: number;
}

export interface SellRequest {
    symbol: string;
    quantity: number;
    unitPrice: number;
    fee: number;
}
