import Toybox.WatchUi;
import Toybox.Lang;

// Only onTap() is delivered to data fields, and only on touch Edge devices
// (1030 / 1030 Plus / 1040 ...). Returning true consumes the tap so the
// built-in home/next-page overlay does NOT pop up.
class ScoutDelegate extends WatchUi.BehaviorDelegate {

    hidden var _view as ScoutView;

    function initialize(view as ScoutView) {
        BehaviorDelegate.initialize();
        _view = view;
    }

    function onTap(evt as WatchUi.ClickEvent) as Boolean {
        var c = evt.getCoordinates();   // absolute screen coords -> field coords
        _view.onScreenTap(c[0], c[1]);  // (assumes the field is the whole page)
        return true;
    }
}
