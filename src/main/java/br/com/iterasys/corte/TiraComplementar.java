package br.com.iterasys.corte;

/**
 * Tira aberta dos dois materiais juntos, na largura do material mais estreito,
 * usada para completar a quantidade de peças que não preencheu uma placa cheia.
 * {@code pecaGirada} indica se a peça precisa ser cortada rotacionada 90° (nunca
 * espelhada) nesta tira.
 */
public record TiraComplementar(double larguraMm, double comprimentoMm, int quantidadePecas, boolean pecaGirada) {
}
