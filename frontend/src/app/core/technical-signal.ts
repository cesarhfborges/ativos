import { TechnicalSignal } from './api.models';

export interface SignalReading {
    label: string;
    icon: string;
    severity: 'info' | 'warn' | 'secondary';
    /** O motivo da leitura, em uma frase. */
    reason: string;
}

const number = (value: number) => value.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

function unavailableReason(signal: TechnicalSignal): string {
    if (signal.closes >= signal.requiredCloses) {
        return 'Os preços deste ativo não são atualizados há mais de 5 dias.';
    }
    return `São necessários ${signal.requiredCloses} fechamentos diários; há ${signal.closes}.`;
}

/**
 * Descreve a situação sem dizer "compre" ou "venda", e nunca separa o momento da tendência: um RSI baixo
 * em tendência de baixa pode ser só um ativo que continua caindo. As cores evitam o verde e o vermelho de ganho e perda.
 */
export function signalReading(signal: TechnicalSignal): SignalReading {
    if (!signal.available || signal.rsi === null) {
        return { label: 'Dados insuficientes', icon: 'pi pi-minus-circle', severity: 'secondary', reason: unavailableReason(signal) };
    }
    const trend = signal.trend === 'UP' ? 'média de 20 dias acima da de 50: tendência de alta' : 'média de 20 dias abaixo da de 50: tendência de baixa';
    const rsi = `RSI ${number(signal.rsi)}`;
    if (signal.momentum === 'OVERSOLD') {
        return {
            label: 'Sobrevendido',
            icon: 'pi pi-arrow-down',
            severity: 'info',
            reason: signal.trend === 'UP' ? `${rsi}, até ${signal.oversoldLimit}; ${trend}. Possível recuo dentro de uma alta.` : `${rsi}, até ${signal.oversoldLimit}; ${trend}. Pode continuar caindo.`
        };
    }
    if (signal.momentum === 'OVERBOUGHT') {
        return {
            label: 'Sobrecomprado',
            icon: 'pi pi-arrow-up',
            severity: 'warn',
            reason: signal.trend === 'UP' ? `${rsi}, a partir de ${signal.overboughtLimit}; ${trend}. Pode continuar subindo.` : `${rsi}, a partir de ${signal.overboughtLimit}; ${trend}. Possível repique dentro de uma queda.`
        };
    }
    return { label: 'Neutro', icon: 'pi pi-minus', severity: 'secondary', reason: `${rsi}, entre ${signal.oversoldLimit} e ${signal.overboughtLimit}; ${trend}.` };
}
