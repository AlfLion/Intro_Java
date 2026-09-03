package br.com.iterasys.corte;

/**
 * Resultado do cálculo de um item da ordem de serviço: exatamente um dos dois
 * planos é preenchido, dependendo se o item usa material único ou conjugado.
 */
public record ResultadoItem(ItemOrdemServico item, NestingResultado nestingMaterialUnico, PlanoConjugado planoConjugado) {
}
