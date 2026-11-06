import { format, parseISO } from 'date-fns';
import { SentimentReading } from './api.models';
import { SentimentTone } from './chart-theme';

export interface SentimentZone {
    label: string;
    tone: SentimentTone;
    /** Nota inicial da faixa, inclusive. */
    from: number;
}

/** Em ordem crescente. A faixa neutra é estreita de propósito: o índice raramente fica exatamente no meio. */
export const SENTIMENT_ZONES: readonly SentimentZone[] = [
    { label: 'Medo extremo', tone: 'extremeFear', from: 0 },
    { label: 'Medo', tone: 'fear', from: 25 },
    { label: 'Neutro', tone: 'neutral', from: 45 },
    { label: 'Ganância', tone: 'greed', from: 56 },
    { label: 'Ganância extrema', tone: 'extremeGreed', from: 76 }
];

/** Onde a grade do gráfico marca as mudanças de faixa. */
export const SENTIMENT_TICKS = [0, 25, 45, 55, 75, 100];

export interface SentimentBand {
    tone: SentimentTone;
    from: number;
    to: number;
}

/**
 * As faixas na nota sem arredondar, para desenhar. Cada uma começa meio ponto antes do número escrito: 44,5 já aparece
 * como 45, que é Neutro, então a cor muda ali.
 */
export const SENTIMENT_BANDS: readonly SentimentBand[] = SENTIMENT_ZONES.map((zone, index) => {
    const next = SENTIMENT_ZONES[index + 1];
    return { tone: zone.tone, from: zone.from === 0 ? 0 : zone.from - 0.5, to: next ? next.from - 0.5 : 100 };
});

export interface GaugePoint {
    x: number;
    y: number;
}

export const GAUGE_CENTER: GaugePoint = { x: 120, y: 120 };

/** Do ponto mais à esquerda (0) ao mais à direita (100), passando pelo topo. Notas fora da escala param nas pontas. */
export function gaugePoint(score: number, radius: number): GaugePoint {
    const angle = Math.PI * (1 - Math.min(100, Math.max(0, score)) / 100);
    return { x: GAUGE_CENTER.x + radius * Math.cos(angle), y: GAUGE_CENTER.y - radius * Math.sin(angle) };
}

/** Caminho SVG de um arco no sentido horário, de uma nota até outra. */
export function gaugeArc(from: number, to: number, radius: number): string {
    const start = gaugePoint(from, radius);
    const end = gaugePoint(to, radius);
    return `M ${start.x.toFixed(2)} ${start.y.toFixed(2)} A ${radius} ${radius} 0 0 1 ${end.x.toFixed(2)} ${end.y.toFixed(2)}`;
}

/** A nota é arredondada antes, para o texto não contradizer o número mostrado. */
export function sentimentZone(score: number): SentimentZone {
    const rounded = Math.round(score);
    return SENTIMENT_ZONES.filter((zone) => rounded >= zone.from).at(-1) ?? SENTIMENT_ZONES[0];
}

export interface SentimentComponent {
    key: string;
    label: string;
    score: number;
    hint: string;
}

export function sentimentComponents(reading: SentimentReading): SentimentComponent[] {
    return [
        { key: 'rsi', label: 'Força das altas', score: reading.rsi, hint: 'RSI de 14 dias: o peso das altas recentes sobre as quedas.' },
        { key: 'trend', label: 'Distância da média', score: reading.trend, hint: 'Preço contra a média de 50 fechamentos, medido na oscilação de cada ativo.' },
        { key: 'range', label: 'Posição na faixa', score: reading.range, hint: 'Onde o preço está entre a mínima e a máxima dos últimos 50 fechamentos.' },
        { key: 'volatility', label: 'Calmaria', score: reading.volatility, hint: 'Oscilação dos últimos 20 fechamentos contra a dos últimos 50. Mais oscilação puxa para o medo.' }
    ];
}

/** A parte da carteira fora da conta, para a nota não parecer falar de tudo. Nulo quando cobre a carteira inteira. */
export function coverageNote(coverage: number): string | null {
    const percent = Math.round(coverage * 100);
    return percent >= 100 ? null : `Considera ${percent}% do valor da carteira. Ativos com menos de 51 fechamentos diários ficam de fora.`;
}

/** O dia vem sem horário: parseISO o mantém na data certa em qualquer fuso, o que new Date() não faria. */
export const sentimentDay = (day: string) => format(parseISO(day), 'dd/MM/yy');
