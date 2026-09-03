package br.com.iterasys.corte;

import java.util.List;
import java.util.Map;

/**
 * Resumo do cálculo de uma ordem de serviço inteira: o resultado individual de
 * cada item, o consumo total de cada material (nome -&gt; mm de rolo consumidos,
 * somando placas cheias e tiras de todos os itens que usam aquele material) e a
 * lista de sobras geradas (uma por item que precisou de tira complementar).
 */
public record ResumoOrdemServico(List<ResultadoItem> resultadosPorItem,
                                  Map<String, Double> consumoTotalPorMaterialMm,
                                  List<SobraMaterial> sobras) {
}
