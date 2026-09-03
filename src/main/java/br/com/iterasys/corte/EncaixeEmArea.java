package br.com.iterasys.corte;

/**
 * Resultado do encaixe de uma peça em uma área limitada tanto em largura quanto em
 * comprimento (ex.: uma placa de tamanho fixo). {@code pecaGirada} indica se a peça
 * precisa ser cortada rotacionada 90° (nunca espelhada) para chegar a este resultado.
 */
public record EncaixeEmArea(int pecasPorFileira, int fileiras, int capacidadeTotal, boolean pecaGirada) {
}
